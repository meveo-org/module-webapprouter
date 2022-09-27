package org.meveo.script;

import java.io.BufferedReader;
import java.io.File;
import java.util.Map;

import org.meveo.admin.exception.BusinessException;
import org.meveo.commons.utils.MeveoFileUtils;
import org.meveo.commons.utils.ParamBean;
import org.meveo.model.crm.EntityReferenceWrapper;
import org.meveo.model.customEntities.CustomEntityInstance;
import org.meveo.model.git.GitRepository;
import org.meveo.service.git.GitHelper;
import org.meveo.service.git.GitRepositoryService;
import org.meveo.service.script.Script;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.InputStreamReader;
import org.meveo.commons.utils.StringUtils;

public class CompileApp extends Script {

    private GitRepositoryService gitRepositoryService = getCDIBean(GitRepositoryService.class);

    @Override
    public void execute(Map<String, Object> methodContext) throws BusinessException {
        CustomEntityInstance webapp = (CustomEntityInstance) methodContext.get(Script.CONTEXT_ENTITY);

        String webAppCode = webapp.get("code");
        EntityReferenceWrapper repository = webapp.get("repository");

        Logger LOG = LoggerFactory.getLogger("Webapp-Compile-" + webAppCode);

        GitRepository uiRepo = gitRepositoryService.findByCode(repository.getCode());

        LOG.info("Compiling files in repository {}", uiRepo.getCode());

        File gitDir = GitHelper.getRepositoryDir(null, uiRepo);
        File uiDir = StringUtils.isNotBlank(webapp.get("srcPath")) ? new File(gitDir, webapp.get("srcPath")) : gitDir;

        try {
            // Create .env file
            File env = new File(uiDir, ".env");
            String envContent = "webContext=" + ParamBean.getInstance().getProperty("meveo.moduleName", "meveo");
            envContent += "\n" + "authServer=" +  System.getProperty("meveo.keycloak.url");
            MeveoFileUtils.writeAndPreserveCharset(envContent, env);

            // Install dependencies
            ProcessBuilder processBuilder = new ProcessBuilder()
				.command("bash", "-c", "npm install --prefix " + uiDir.getAbsolutePath())
				.redirectErrorStream(true);

            Process process = processBuilder.start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    LOG.info(line);
                }
                process.waitFor();
                LOG.info("Dependencies installed\n");
            }

            // Install webpack and webpacl cli if not installed
            processBuilder = new ProcessBuilder()
                .command("bash", "-c", "npm list -g | grep webpack")
                .redirectErrorStream(true);

            process = processBuilder.start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                process.waitFor();

                if (reader.lines().count() == 0) {
                    LOG.info("Installing webpack ...");
                    processBuilder = new ProcessBuilder()
                    .command("bash", "-c", "npm install -g webpack webpack-cli")
                    .redirectErrorStream(true);

                    process = processBuilder.start();
                    try (BufferedReader reader2 = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                        String line;
                        while ((line = reader2.readLine()) != null) {
                            LOG.info(line);
                        }
                        
                        process.waitFor();
                        LOG.info("Webpack installed\n");
                    }
                } else {
                    LOG.info("Webpack already installed");
                }
            }

            processBuilder = new ProcessBuilder()
                .command("bash", "-c", "npm exec -c 'webpack'")
                .directory(uiDir)
				.redirectErrorStream(true);

            process = processBuilder.start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    LOG.info(line);
                }
                process.waitFor();
                LOG.info("Webapp compiled\n");
            }

        } catch (Exception e) {
            LOG.error("Failed to compile", e);
        }
    }


}
