/*
 * (C) Copyright 2018-2019 Manaty SARL (https://manaty.net) and contributors.
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the
 * GNU Affero General Public License as published by the Free Software Foundation, either version 3
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. This program is
 * not suitable for any direct or indirect application in MILITARY industry See the GNU Affero
 * General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License along with this program.
 * If not, see <http://www.gnu.org/licenses/>.
 */
package org.manaty.webapp;

import org.meveo.api.exception.EntityDoesNotExistsException;
import org.meveo.api.persistence.CrossStorageApi;
import org.meveo.api.rest.technicalservice.impl.EndpointRequest;
import org.meveo.api.rest.technicalservice.impl.EndpointResponse;
import org.meveo.commons.utils.ParamBean;
import org.meveo.commons.utils.ParamBeanFactory;
import org.meveo.model.crm.EntityReferenceWrapper;
import org.meveo.model.customEntities.CustomEntityInstance;
import org.meveo.model.customEntities.WebApplication;
import org.meveo.model.git.GitRepository;
import org.meveo.model.storage.Repository;
import org.meveo.service.custom.CustomEntityInstanceService;
import org.meveo.service.git.GitHelper;
import org.meveo.service.git.GitRepositoryService;
import org.meveo.service.script.Script;
import org.meveo.service.storage.RepositoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Map;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class WebApp extends Script {

    private static final int DEFAULT_BUFFER_SIZE = 10240;
    private static final long DEFAULT_EXPIRE_TIME = 604800000L;
    private static final String CRLF = "\r\n";
    private static final String ENDPOINT_URL = "/rest/webapp/";
    private static final String INDEX_FILE = "index.html";
    private static final String PNG_TYPE = "image/png";
    private static final String DEFAULT_ICON = "https://avatars1.githubusercontent.com/u/59589948?s=200&v=4";
    private static final String INDEX_REPLACE_START = "<!-- REPLACE TEMPLATE SECTION START -->";
    private static final String INDEX_REPLACE_END = "<!-- REPLACE TEMPLATE SECTION END -->";
    private static final String TITLE_TEMPLATE = "<title>%s</title>";
    private static final String FAVICON_TEMPLATE = "<link rel=\"icon\" type=\"%s\" href=\"%s\" />";
    private static final String BASEURL_TEMPLATE = "<base href=\"%s\" />";

    private static final Logger LOG = LoggerFactory.getLogger(WebApp.class);

    private ParamBeanFactory paramBeanFactory = getCDIBean(ParamBeanFactory.class);
    private CustomEntityInstanceService ceiService = getCDIBean(CustomEntityInstanceService.class);
    private RepositoryService repositoryService = getCDIBean(RepositoryService.class);
    private CrossStorageApi crossStorageApi = getCDIBean(CrossStorageApi.class);
    private GitRepositoryService gitRepositoryService = getCDIBean(GitRepositoryService.class);

    private ParamBean config = paramBeanFactory.getInstance();
    private Repository repository = repositoryService.findDefaultRepository();

    private EndpointRequest request;
    private final String webappRouterTargetDirectory;
    private Object result = "";
    private String appCode = null;
    private WebApplication app;
    private static boolean webappRouterTargetDirectoryChecked = false;

    public WebApp() {
        String meveoProviderBaseDirectory = config.getProperty("providers.rootDir", File.separator + "meveodata");
        String providerRootDirectory = config.getProperty("provider.rootDir", "default");
        meveoProviderBaseDirectory += File.separator + providerRootDirectory + File.separator;
        webappRouterTargetDirectory = meveoProviderBaseDirectory + "webapp" + File.separator;
        LOG.info("webappRouterTargetDirectory: {}", webappRouterTargetDirectory);
        File path = new File(webappRouterTargetDirectory);
        if (!webappRouterTargetDirectoryChecked && !path.exists()) {
            path.mkdirs();
            if (path.exists())
                webappRouterTargetDirectoryChecked = true;
        }
    }

    public Object getResult() {
        return result;
    }

    public String getAppCode() {
        return appCode;
    }

    public void setAppCode(String appCode) {
        this.appCode = appCode;
    }

    @Override
    public void execute(Map<String, Object> methodContext) {
        this.request = (EndpointRequest) methodContext.get("request");
        EndpointResponse response = (EndpointResponse) methodContext.get("response");
        String remainingPath = request.getRemainingPath();
        LOG.info("appCode: " + this.appCode);
        LOG.info("remainingPath: " + remainingPath);

        app = crossStorageApi.find(repository, WebApplication.class).by("code", this.appCode).getResult();

        if (app == null) {
            LOG.error("WebApplication with code: {}, does not exist.", this.appCode);
            response.setStatus(400);
            result = "Encountered error while trying to load " + remainingPath;
            return;
        }

        GitRepository appGitRepository = app.getRepository();
        appGitRepository = gitRepositoryService.findById(appGitRepository.getId());
        app.setRepository(appGitRepository);
        LOG.info("repo: " + appGitRepository.getClass());
        LOG.info("repoCode: " + appGitRepository.getCode());
        LOG.info("repoId: " + appGitRepository.getId());

        String appPath = "/" + appGitRepository.getCode();
        String rootPath = null;
        if (remainingPath.equalsIgnoreCase(appPath)) {
            rootPath = webappRouterTargetDirectory;
        } else {
            rootPath = webappRouterTargetDirectory + appGitRepository.getCode();
        }

        if (app.getROOT_PATH() != null) {
            rootPath = new File(rootPath, app.getROOT_PATH()).getPath();
        }


        try {
            // we first try to get the file from file explorer under the webapp/appCode/
            // directory
            File file = lookupFile(rootPath, remainingPath);
            if (file == null) {
                LOG.info("File not found in webapp, we look in git : " + rootPath);
                File repositoryDir = GitHelper.getRepositoryDir(null, appGitRepository);
                rootPath = repositoryDir.getPath().toString();
                if (app.getROOT_PATH() != null) {
                    rootPath = new File(rootPath, app.getROOT_PATH()).getPath();
                }
                file = lookupFile(rootPath, remainingPath);

                // file still doesnt exist, we build it
                if (file == null) {
                    LOG.info("File not found in git, we build it");
                    CustomEntityInstance instance = crossStorageApi.find(repository, app.getUuid(), app.getCetCode());
                    result = org.manaty.webapp.HtmlApplicationSerializer.getHtml(instance, remainingPath, ceiService);
                    return;
                }
            }
            serveFile(file, request, response);
        } catch (IOException | EntityDoesNotExistsException exception) {
            response.setStatus(400);
            result = "Encountered error while trying to load " + remainingPath;
        }
    }

    private File lookupFile(String rootPath, String remainingPath) throws java.io.IOException {
        // load the file as-is at first
        LOG.info("Looking for " + remainingPath + " in " + rootPath);
        File file = new File(rootPath, URLDecoder.decode(remainingPath, "UTF-8"));

        // we attempt to load the index.html from the directory first.
        if (!file.exists() || file.isDirectory()) {
            if (file.isDirectory()) {
                int subFolderIndex = remainingPath.indexOf("/", 1);
                String subFolder = subFolderIndex > -1 ? remainingPath.substring(0, subFolderIndex) : remainingPath;
                LOG.info("Attempt to load index.html from " + subFolder);
                file = new File(rootPath, URLDecoder.decode(subFolder + File.separator + INDEX_FILE, "UTF-8"));
            }
            // default to rootPath's index.html
            if (!file.exists()) {
                LOG.info("Attempt to load index.html from " + rootPath);
                String baseIndexPath = rootPath + File.separator;
                File indexTemplate = new File(baseIndexPath + INDEX_FILE);


                File repositoryDir = GitHelper.getRepositoryDir(null, app.getRepository());
                String repoPath = repositoryDir.getPath().toString();
                if (app.getROOT_PATH() != null) {
                    repoPath = new File(repoPath, app.getROOT_PATH()).getPath();
                }

                String rootIndex = repoPath + File.separator + INDEX_FILE;
                boolean isAppIndex = indexTemplate.getAbsolutePath().contains(rootIndex);

                LOG.info("indexPath: {}", indexTemplate.getAbsolutePath());
                LOG.info("repoPath: {}", repoPath);
                LOG.info("rootIndex: {}", rootIndex);
                LOG.info("isAppIndex: {}", isAppIndex);
                if (isAppIndex) {
                    return new File(rootIndex);
                }
            }
            // if an index.html file does not exist in both rootPath and subdirectory, we
            // return null
            if (!file.exists()) {
                return null;
            }
        }
        LOG.info("Lookup returning file: {}", file.toPath());
        return file;
    }

    private void serveFile(File file, EndpointRequest request, EndpointResponse response) {
        LOG.info("Serving file " + file.getAbsolutePath());
        // Prepare some variables. The ETag is an unique identifier of the file.
        String fileName = file.getName();
        long length = file.length();
        long lastModified = file.lastModified();
        String eTag = fileName + "_" + length + "_" + lastModified;
        long expires = System.currentTimeMillis() + DEFAULT_EXPIRE_TIME;
        // Validate request headers for caching
        // ---------------------------------------------------
        // If-None-Match header should contain "*" or ETag. If so, then return 304.
        String ifNoneMatch = request.getHeader("If-None-Match");
        if (ifNoneMatch != null && matches(ifNoneMatch, eTag)) {
            response.setStatus(304);
            // Required in 304.
            response.setHeader("ETag", eTag);
            // Postpone cache with 1 week.
            response.setDateHeader("Expires", expires);
            LOG.info("If-None-Match");
            return;
        }
        // If-Modified-Since header should be greater than LastModified. If so, then
        // return 304.
        // This header is ignored if any If-None-Match header is specified.
        long ifModifiedSince = request.getDateHeader("If-Modified-Since");
        if (ifNoneMatch == null && ifModifiedSince != -1 && ifModifiedSince + 1000 > lastModified) {
            response.setStatus(304);
            // Required in 304.
            response.setHeader("ETag", eTag);
            // Postpone cache with 1 week.
            response.setDateHeader("Expires", expires);
            LOG.info("If-Modified-Match");
            return;
        }
        // Validate request headers for resume
        // ----------------------------------------------------
        // If-Match header should contain "*" or ETag. If not, then return 412.
        String ifMatch = request.getHeader("If-Match");
        if (ifMatch != null && !matches(ifMatch, eTag)) {
            response.setStatus(412);
            LOG.info("If-Match");
            return;
        }
        // If-Unmodified-Since header should be greater than LastModified. If not, then
        // return 412.
        long ifUnmodifiedSince = request.getDateHeader("If-Unmodified-Since");
        if (ifUnmodifiedSince != -1 && ifUnmodifiedSince + 1000 <= lastModified) {
            response.setStatus(412);
            LOG.info("If-Unmodified-Since");
            return;
        }
        // Prepare and initialize response
        // --------------------------------------------------------
        // Get content type by file name and set default GZIP support and content
        // disposition.
        String contentType = request.getServletContext().getMimeType(fileName);
        LOG.info("Servlet context found MIME=" + contentType);
        boolean acceptsGzip = false;
        String disposition = "inline";
        // To add new content types, add new mime-mapping entry in web.xml.
        if (contentType == null) {
            if (fileName.endsWith(".js")) {
                contentType = "application/javascript";
            }
            contentType = "application/octet-stream";
        }
        // the browser and expand content type with the one and right character
        // encoding.
        if (contentType.startsWith("text")) {
            String acceptEncoding = request.getHeader("Accept-Encoding");
            acceptsGzip = acceptEncoding != null && accepts(acceptEncoding, "gzip");
            contentType += ";charset=UTF-8";
        } else // the browser, then set to inline, else attachment which will pop a 'save as'
            // dialogue.
            if (!contentType.startsWith("image")) {
                String accept = request.getHeader("Accept");
                disposition = accept != null && accepts(accept, contentType) ? "inline" : "attachment";
            }
        LOG.info(" content-type:" + contentType);
        // Initialize response.
        response.setBufferSize(DEFAULT_BUFFER_SIZE);
        response.setContentType(contentType);
        response.setHeader("Content-Disposition", disposition + ";filename=\"" + fileName + "\"");
        response.setHeader("Accept-Ranges", "bytes");
        response.setHeader("ETag", eTag);
        response.setDateHeader("Last-Modified", lastModified);
        response.setDateHeader("Expires", expires);
        try {
            response.setOutput(Files.readAllBytes(file.toPath()));
        } catch (IOException ioException) {
            response.setStatus(400);
            response.setErrorMessage("Encountered error while trying to load " + fileName);
        }
    }

    // Helpers (can be refactored to public utility class)
    // ----------------------------------------

    /**
     * Returns true if the given accept header accepts the given value.
     *
     * @param acceptHeader The accept header.
     * @param toAccept     The value to be accepted.
     * @return True if the given accept header accepts the given value.
     */
    private static boolean accepts(String acceptHeader, String toAccept) {
        String[] acceptValues = acceptHeader.split("\\s*(,|;)\\s*");
        Arrays.sort(acceptValues);
        return Arrays.binarySearch(acceptValues, toAccept) > -1
            || Arrays.binarySearch(acceptValues, toAccept.replaceAll("/.*$", "/*")) > -1
            || Arrays.binarySearch(acceptValues, "*/*") > -1;
    }

    /**
     * Returns true if the given match header matches the given value.
     *
     * @param matchHeader The match header.
     * @param toMatch     The value to be matched.
     * @return True if the given match header matches the given value.
     */
    private static boolean matches(String matchHeader, String toMatch) {
        String[] matchValues = matchHeader.split("\\s*,\\s*");
        Arrays.sort(matchValues);
        return Arrays.binarySearch(matchValues, toMatch) > -1 || Arrays.binarySearch(matchValues, "*") > -1;
    }
}


class HtmlApplicationSerializer {

    static final String docType = "<!DOCTYPE html>";

    static final String ln = System.lineSeparator();

    static CustomEntityInstanceService customEntityInstanceService;

    static String getHtml(CustomEntityInstance app, String remainingPath, CustomEntityInstanceService ceis) {
        customEntityInstanceService = ceis;
        if (app.getCfValues().getCfValue("webPages") == null) {
            return docType + "<html><body><h1>" + app.getDescription() + "</h1></body></html>";
        }
        Map<String, EntityReferenceWrapper> webpages = (Map<String, EntityReferenceWrapper>) app.getCfValues()
                                                                                                .getCfValue("webPages")
                                                                                                .getMapValue();
        EntityReferenceWrapper webpage = null;
        if (webpages != null && webpages.containsKey(remainingPath)) {
            webpage = webpages.get(remainingPath);
        }
        if (webpage == null) {
            return docType + "<html><body><h1>Page " + remainingPath + " not found among "
                + app.getCfValues().getValues() + "</h1></body></html>";
        }
        StringBuilder result = new StringBuilder();
        result.append(docType).append(ln);
        result.append("<html>").append(ln);
        result.append("<head>").append(ln);
        if (app.getCfValues().getCfValue("stylesheets") != null) {
            Map<String, EntityReferenceWrapper> stylesheets = (Map<String, EntityReferenceWrapper>) app.getCfValues()
                                                                                                       .getCfValue(
                                                                                                           "stylesheets")
                                                                                                       .getListValue();
            if (stylesheets != null && stylesheets.size() > 0) {
                result.append(getStyleSheets(stylesheets));
            }
        }
        result.append(getWebpageHtml(webpage));
        result.append("</html>").append(ln);
        return result.toString();
    }

    static String getWebpageHtml(EntityReferenceWrapper webpageWrapper) {
        StringBuilder result = new StringBuilder();
        CustomEntityInstance webpage = customEntityInstanceService.findByCodeByCet("ApplicationWebPage",
            webpageWrapper.getCode());
        result.append("<title>").append(webpage.getDescription()).append("</title>");
        if (webpage.getCfValues().getCfValue("stylesheets") != null) {
            Map<String, EntityReferenceWrapper> stylesheets = (Map<String, EntityReferenceWrapper>) webpage
                .getCfValues().getCfValue("stylesheets").getListValue();
            if (stylesheets != null && stylesheets.size() > 0) {
                result.append(getStyleSheets(stylesheets));
            }
        }
        result.append("</head>").append(ln);
        if (webpage.getCfValues().getCfValue("body") != null) {
            result.append("<body>").append(ln).append(webpage.getCfValues().getCfValue("body").getStringValue())
                  .append("</body>").append(ln);
        }
        return result.toString();
    }

    static String getStyleSheets(Map<String, EntityReferenceWrapper> stylesheets) {
        StringBuilder result = new StringBuilder();
        for (EntityReferenceWrapper stylesheetWrapper : stylesheets.values()) {
            CustomEntityInstance stylesheet = customEntityInstanceService.findByCodeByCet("CSSStyleSheet",
                stylesheetWrapper.getCode());
            if (stylesheet.getCfValues().getCfValue("externalURL") != null) {
                result.append("<link rel=\"stylesheet\" href=\"")
                      .append(stylesheet.getCfValues().getCfValue("externalURL").getStringValue()).append("\">")
                      .append(ln);
            } else {
                result.append("<style>").append(stylesheet.getCfValues().getCfValue("content").getStringValue())
                      .append("</style>").append(ln);
            }
        }
        return result.toString();
    }
}


class WebAppScriptHelper {
    private static final String WORD_REGEX = "(?<=[a-z])(?=[A-Z])|(?<=[A-Z])(?=[A-Z][a-z])|_|\\s|-";
    private static final String EMPTY = "";
    private static final String SPACE = " ";
    private static final String DASH = "-";
    private static final String UNDERSCORE = "_";
    private static final UnaryOperator<String> TITLE_CASE = word -> word.isEmpty() ? word
        : Character.toTitleCase(word.charAt(0)) + word.substring(1).toLowerCase();
    private static final UnaryOperator<String> UPPER_CASE = word -> word.isEmpty() ? word : word.toUpperCase();
    private static final UnaryOperator<String> LOWER_CASE = word -> word.isEmpty() ? word : word.toLowerCase();

    private static final String convert(String input, UnaryOperator<String> mapper, String joinCharacter) {
        if (input == null || input.isEmpty()) {
            return "";
        }
        Pattern pattern = Pattern.compile(WORD_REGEX);
        Matcher matcher = pattern.matcher(input);
        String text = matcher.replaceAll(SPACE);
        String[] words = text.split(SPACE);
        return Arrays.stream(words).map(mapper).collect(Collectors.joining(joinCharacter));
    }

    static final String toTitleName(String input) {
        return convert(input, TITLE_CASE, SPACE);
    }

    static final UnaryOperator<String> TITLE = WebAppScriptHelper::toTitleName;

    static final String toConstantName(String input) {
        return convert(input, UPPER_CASE, UNDERSCORE);
    }

    static final UnaryOperator<String> CONSTANT = WebAppScriptHelper::toConstantName;

    static final String toVariableName(String input) {
        String name = convert(input, TITLE_CASE, EMPTY);
        return Character.toLowerCase(name.charAt(0)) + name.substring(1);
    }

    static final UnaryOperator<String> VARIABLE = WebAppScriptHelper::toVariableName;

    static final String toPascalName(String input) {
        String name = convert(input, TITLE_CASE, EMPTY);
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    static final UnaryOperator<String> PASCAL = WebAppScriptHelper::toPascalName;

    static final String toTagName(String input) {
        return convert(input, LOWER_CASE, DASH);
    }

    static final UnaryOperator<String> TAG = WebAppScriptHelper::toTagName;
}
