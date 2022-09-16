package org.meveo.script;

import java.util.List;
import java.util.Map;

import org.meveo.admin.exception.BusinessException;
import org.meveo.api.persistence.CrossStorageApi;
import org.meveo.model.customEntities.CustomEntityInstance;
import org.meveo.model.customEntities.WebApplication;
import org.meveo.model.git.GitRepository;
import org.meveo.model.persistence.CEIUtils;
import org.meveo.security.PasswordUtils;
import org.meveo.service.git.GitClient;
import org.meveo.service.script.Script;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AutoUpdateJob extends Script {

    private static Logger LOG = LoggerFactory.getLogger(AutoUpdateJob.class);

    private CrossStorageApi api = getCDIBean(CrossStorageApi.class);
    private GitClient gitClient = getCDIBean(GitClient.class);

    @Override
    public void execute(Map<String, Object> methodContext) throws BusinessException {
        List<WebApplication> webAppsToPull = api.find(WebApplication.class)
            .by("autoUpdate", true)
            .fetch("repository")
            .fetch("code")
            .getResults();

        CompileApp compileAppScript = new CompileApp();

        for (WebApplication webApplication : webAppsToPull) {
            GitRepository webAppRepository = webApplication.getRepository();
            String pwd = PasswordUtils.decrypt(webAppRepository.getSalt(), webAppRepository.getDefaultRemotePassword());
            boolean hasChanges = gitClient.pull(webAppRepository, webAppRepository.getDefaultRemoteUsername(), pwd);
            if (hasChanges) {
                LOG.info("Compiling webapp {}", webApplication.getCode());
                CustomEntityInstance webAppCei = CEIUtils.pojoToCei(webApplication);
                compileAppScript.execute(Map.of(Script.CONTEXT_ENTITY, webAppCei));
            } else {
                LOG.info("Webapp {} has no changes", webApplication.getCode());
            }
        }
    }
    
}
