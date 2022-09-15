package org.meveo.script;

import java.io.File;
import java.io.IOException;
import java.util.Map;

import org.meveo.admin.exception.BusinessException;
import org.meveo.commons.utils.NpmHelper;
import org.meveo.model.customEntities.CustomEntityInstance;
import org.meveo.model.git.GitRepository;
import org.meveo.service.git.GitHelper;
import org.meveo.service.git.GitRepositoryService;
import org.meveo.service.script.Script;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CompileApp extends Script {

    private GitRepositoryService gitRepositoryService = getCDIBean(GitRepositoryService.class);

    private static Logger LOG = LoggerFactory.getLogger(CompileApp.class);
    
    @Override
    public void execute(Map<String, Object> methodContext) throws BusinessException {
        CustomEntityInstance webapp = (CustomEntityInstance) methodContext.get(Script.CONTEXT_ENTITY);

        String webAppCode = webapp.get("code");
        GitRepository uiRepo = gitRepositoryService.findByCode(webAppCode + "-UI");

        File uiDir = GitHelper.getRepositoryDir(null, uiRepo);

        try {
            NpmHelper.npmInstallDependencies(uiDir);
            LOG.info("Dependencies installed");

            NpmHelper.npmRun(uiDir, "build-prod");
            LOG.info("Webapp compiled");
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }


}
