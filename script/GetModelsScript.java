package org.meveo.script;

import static java.nio.file.StandardCopyOption.COPY_ATTRIBUTES;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.Permission;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Set;
import java.util.Map.Entry;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import com.fasterxml.jackson.core.type.TypeReference;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.meveo.admin.exception.BusinessException;
import org.meveo.api.persistence.CrossStorageApi;
import org.meveo.commons.utils.ParamBean;
import org.meveo.commons.utils.ParamBeanFactory;
import org.meveo.model.crm.CustomFieldTemplate;
import org.meveo.model.crm.custom.CustomFieldTypeEnum;
import org.meveo.model.crm.custom.EntityCustomAction;
import org.meveo.model.customEntities.CustomEntityInstance;
import org.meveo.model.customEntities.CustomEntityTemplate;
import org.meveo.model.customEntities.WebApplication;
import org.meveo.model.git.GitRepository;
import org.meveo.model.module.MeveoModule;
import org.meveo.model.module.MeveoModuleItem;
import org.meveo.model.persistence.JacksonUtil;
import org.meveo.model.storage.Repository;
import org.meveo.persistence.CrossStorageService;
import org.meveo.security.MeveoUser;
import org.meveo.service.admin.impl.MeveoModuleService;
import org.meveo.service.crm.impl.CustomFieldInstanceService;
import org.meveo.service.crm.impl.CustomFieldTemplateService;
import org.meveo.service.custom.CustomEntityTemplateService;
import org.meveo.service.custom.EntityCustomActionService;
import org.meveo.service.git.GitClient;
import org.meveo.service.git.GitHelper;
import org.meveo.service.git.GitRepositoryService;
import org.meveo.service.script.Script;
import org.meveo.service.storage.RepositoryService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GetModelsScript extends Script {
	private static final String MASTER_BRANCH = "master";
	private static final String MEVEO_BRANCH = "meveo";
	private static final String MV_TEMPLATE_REPO = "https://github.com/meveo-org/mv-template.git";
	private static final String LOG_SEPARATOR = "***********************************************";
	private static final String CUSTOM_TEMPLATE = CustomEntityTemplate.class.getName();
	private static final String WEB_APP_TEMPLATE = WebApplication.class.getSimpleName();
	private static final String PARENT = "Parent";
	private static final String PAGE_TEMPLATE = "Parent.js";
	private static final String INDEX_TEMPLATE = "index.js";
	private static final String LOCALHOST = "http://localhost:8080/";
	private static final String KEYCLOAK_URL = "http://host.docker.internal:8081/auth";
	private static final String KEYCLOAK_REALM = "meveo";
	private static final String KEYCLOAK_RESOURCE = "meveo-web";
	private static final String AFFIX = "-UI";
	private static final Logger LOG = LoggerFactory.getLogger(GetModelsScript.class);
	private String CRLF = WebAppScriptHelper.CRLF;
	private String SLASH = File.separator;
	private String baseUrl = null;
	private CrossStorageService crossStorageService = getCDIBean(CrossStorageService.class);
	private CustomEntityTemplateService cetService = getCDIBean(CustomEntityTemplateService.class);
	private CustomFieldInstanceService cfiService = getCDIBean(CustomFieldInstanceService.class);
	private CustomFieldTemplateService cftService = getCDIBean(CustomFieldTemplateService.class);
	private EntityCustomActionService ecaService = getCDIBean(EntityCustomActionService.class);
	private GitClient gitClient = getCDIBean(GitClient.class);
	private GitRepositoryService gitRepositoryService = getCDIBean(GitRepositoryService.class);
	private MeveoModuleService meveoModuleService = getCDIBean(MeveoModuleService.class);
	private RepositoryService repositoryService = getCDIBean(RepositoryService.class);
	private ParamBeanFactory paramBeanFactory = getCDIBean(ParamBeanFactory.class);
	private CrossStorageApi crossStorageApi = getCDIBean(CrossStorageApi.class);

	private Repository repository;
	private String moduleCode;

	public String getModuleCode() {
		return this.moduleCode;
	}

	public void setModuleCode(String moduleCode) {
		this.moduleCode = moduleCode;
	}

	public Repository getDefaultRepository() {
		if (repository == null) {
			repository = repositoryService.findDefaultRepository();
		}
		return repository;
	}

	@Override
	public void execute(Map<String, Object> parameters) throws BusinessException {
		LOG.debug("START - GetModelsScript.execute()");
		super.execute(parameters);
		LOG.debug("moduleCode: {}", moduleCode);
		if (moduleCode == null) {
			throw new BusinessException("moduleCode not set");
		}
		MeveoModule module = meveoModuleService.findByCode(moduleCode);
		MeveoUser user = (MeveoUser) parameters.get(CONTEXT_CURRENT_USER);
		ParamBean appConfig = paramBeanFactory.getInstance();
		String remoteUrl = appConfig.getProperty("meveo.git.directory.remote.url", null);
		String remoteUsername = appConfig.getProperty("meveo.git.directory.remote.username", null);
		String remotePassword = appConfig.getProperty("meveo.git.directory.remote.password", null);
		String appContext = appConfig.getProperty("meveo.moduleName", "");
		String serverUrl = appConfig.getProperty("meveo.admin.baseUrl", null);
		String keycloakUrl = System.getProperty("meveo.keycloak.url");
		String keycloakRealm = System.getProperty("meveo.keycloak.realm");
		String keycloakResource = System.getProperty("meveo.keycloak.client");

		this.baseUrl = serverUrl;
		if (this.baseUrl == null) {
			this.baseUrl = LOCALHOST;
		}

		this.baseUrl = this.baseUrl.strip().endsWith("/") ? this.baseUrl : this.baseUrl + "/";
		this.baseUrl = this.baseUrl + appContext;

		if (module != null) {
			LOG.debug("Module found: {}", module.getCode());
			Set<MeveoModuleItem> moduleItems = module.getModuleItems();
			List<String> entityCodes =
					moduleItems.stream().filter(item -> CUSTOM_TEMPLATE.equals(item.getItemClass()))
							.map(entity -> entity.getItemCode()).collect(Collectors.toList());
			LOG.debug("entityCodes: {}", entityCodes);

			File moduleWebAppDirectory = GitHelper.getRepositoryDir(user, moduleCode + AFFIX);
			Path moduleWebAppPath = moduleWebAppDirectory.toPath();
			Path modelsPath = moduleWebAppPath.resolve("model");
			LOG.debug("modelsPath path: {}", modelsPath.toString());

			try (Stream<Path> modelsStream = Files.walk(modelsPath)) {
				// using user role and permissions, figure out which entities are allowed to be exported
				Set<Permission> permissions = user.getRoles().stream().flatMap(role -> role.getPermissions().stream())
						.collect(Collectors.toSet());
				LOG.debug("permissions: {}", permissions);
				List<String> allowedEntities =
				entityCodes.stream().filter(entity -> {
					return permissions.stream().anyMatch(permission -> permission.getEntityClass().contains(entity));
				}).collect(Collectors.toList());			
				LOG.debug("allowedEntities: {}", allowedEntities);
				
				// generate model index.js

				// return model index.js
			} catch (IOException ioe) {
				throw new BusinessException(ioe);
			}

		} else {
			throw new BusinessException("Module not found: " + moduleCode);
		}
		LOG.debug("END - GetModelsScript.execute()");
	}
}
