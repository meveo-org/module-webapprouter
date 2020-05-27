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

import org.meveo.api.rest.technicalservice.impl.EndpointResponse;
import org.meveo.api.rest.technicalservice.impl.EndpointRequest;
import org.meveo.commons.utils.EjbUtils;
import org.meveo.commons.utils.ParamBean;
import org.meveo.commons.utils.ParamBeanFactory;
import org.meveo.model.crm.EntityReferenceWrapper;
import org.meveo.model.customEntities.CustomEntityInstance;
import org.meveo.service.custom.CustomEntityInstanceService;
import org.meveo.service.script.Script;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.File;
import java.nio.file.Files;
import java.io.IOException;
import java.net.URLDecoder;
import java.util.Arrays;
import java.util.Map;

public class WebApp extends Script {

    // ..bytes = 10KB.
    private static final int DEFAULT_BUFFER_SIZE = 10240;

    // ..ms = 1 week.
    private static final long DEFAULT_EXPIRE_TIME = 604800000L;

    private static final String MULTIPART_BOUNDARY = "MULTIPART_BYTERANGES";

    private static final Logger log = LoggerFactory.getLogger(WebApp.class);

    private static ParamBeanFactory paramBeanFactory = (ParamBeanFactory) EjbUtils.getServiceInterface(ParamBeanFactory.class.getSimpleName());

    private ParamBean config = paramBeanFactory.getInstance();

    private String basePath;

    private String webappPath;

    private Object result = "";

    private String appCode = "";

    CustomEntityInstanceService customEntityInstanceService;

    public WebApp() {
        customEntityInstanceService = (CustomEntityInstanceService) getServiceInterface("CustomEntityInstanceService");
        basePath = config.getProperty("providers.rootDir", File.separator + "meveodata");
        String rootDirectory = config.getProperty("provider.rootDir", "default");
        basePath += File.separator + rootDirectory + File.separator;
        webappPath = basePath + "webapp" + File.separator;
        log.debug("basePath: {}", basePath);
        log.debug("webappPath: {}", basePath);
        File path = new File(webappPath);
        if (!path.exists()) {
            path.mkdirs();
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
        EndpointRequest request = (EndpointRequest) methodContext.get("request");
        EndpointResponse response = (EndpointResponse) methodContext.get("response");
        String remainingPath = request.getRemainingPath();
        log.info("appCode: " + this.appCode);
        log.info("remainingPath: " + remainingPath);
        String appPath = "/" + this.appCode;
        String rootPath = null;
        if (remainingPath.equalsIgnoreCase(appPath)) {
            rootPath = webappPath;
        } else {
            rootPath = webappPath + this.appCode;
        }
        try {
            // we first try to get the file from file explorer under the webapp/appCode/ directory
            File file = lookupFile(rootPath, remainingPath);
            if (file == null) {
                log.info("File not found in webapp, we look in git");
                CustomEntityInstance app = customEntityInstanceService.findByCodeByCet("WEB_APPLICATION", this.appCode);
                rootPath = basePath + app.getCfValues().getCfValue("ROOT_PATH").getStringValue();
                file = lookupFile(rootPath, remainingPath);
                // file still doesnt exist, we build it
                if (file == null) {
                    log.info("File not found in git, we build it");
                    result = org.manaty.webapp.HtmlApplicationSerializer.getHtml(app, remainingPath, customEntityInstanceService);
                    return;
                }
            }
            serveFile(file, request, response);
        } catch (IOException ioException) {
            response.setStatus(400);
            result = "Encountered error while trying to load " + remainingPath;
            return;
        }
    }

    private File lookupFile(String rootPath, String remainingPath) throws java.io.IOException {
        // load the file as-is at first
        File file = new File(rootPath, URLDecoder.decode(remainingPath, "UTF-8"));
        log.info("Looking for " + remainingPath + " in " + rootPath);
        // we attempt to load the index.html from the directory first.
        if (!file.exists() || file.isDirectory()) {
            if (file.isDirectory()) {
                int subFolderIndex = remainingPath.indexOf("/", 1);
                String subFolder = subFolderIndex > -1 ? remainingPath.substring(0, subFolderIndex) : remainingPath;
                log.info("Attempt to load index.html from " + subFolder);
                file = new File(rootPath, URLDecoder.decode(subFolder + File.separator + "index.html", "UTF-8"));
            }
            // default to rootPath's index.html
            if (!file.exists()) {
                log.info("Attempt to load index.html from " + rootPath);
                String indexFile = rootPath + File.separator + "index.html";
                file = new File(indexFile);
            }
            // if an index.html file does not exist in both rootPath and subdirectory, we return null
            if (!file.exists()) {
                return null;
            }
        }
        return file;
    }

    private void serveFile(File file, EndpointRequest request, EndpointResponse response) {
        log.info("Serving file " + file.getAbsolutePath());
        // Prepare some variables. The ETag is an unique identifier of the file.
        String fileName = file.getName();
        long length = file.length();
        long lastModified = file.lastModified();
        String eTag = fileName + "_" + length + "_" + lastModified;
        long expires = System.currentTimeMillis() + DEFAULT_EXPIRE_TIME;
        // Validate request headers for caching ---------------------------------------------------
        // If-None-Match header should contain "*" or ETag. If so, then return 304.
        String ifNoneMatch = request.getHeader("If-None-Match");
        if (ifNoneMatch != null && matches(ifNoneMatch, eTag)) {
            response.setStatus(304);
            // Required in 304.
            response.setHeader("ETag", eTag);
            // Postpone cache with 1 week.
            response.setDateHeader("Expires", expires);
            log.debug("If-None-Match");
            return;
        }
        // If-Modified-Since header should be greater than LastModified. If so, then return 304.
        // This header is ignored if any If-None-Match header is specified.
        long ifModifiedSince = request.getDateHeader("If-Modified-Since");
        if (ifNoneMatch == null && ifModifiedSince != -1 && ifModifiedSince + 1000 > lastModified) {
            response.setStatus(304);
            // Required in 304.
            response.setHeader("ETag", eTag);
            // Postpone cache with 1 week.
            response.setDateHeader("Expires", expires);
            log.debug("If-Modified-Match");
            return;
        }
        // Validate request headers for resume ----------------------------------------------------
        // If-Match header should contain "*" or ETag. If not, then return 412.
        String ifMatch = request.getHeader("If-Match");
        if (ifMatch != null && !matches(ifMatch, eTag)) {
            response.setStatus(412);
            log.debug("If-Match");
            return;
        }
        // If-Unmodified-Since header should be greater than LastModified. If not, then return 412.
        long ifUnmodifiedSince = request.getDateHeader("If-Unmodified-Since");
        if (ifUnmodifiedSince != -1 && ifUnmodifiedSince + 1000 <= lastModified) {
            response.setStatus(412);
            log.debug("If-Unmodified-Since");
            return;
        }
        // Prepare and initialize response --------------------------------------------------------
        // Get content type by file name and set default GZIP support and content disposition.
        String contentType = request.getServletContext().getMimeType(fileName);
        log.debug("Servlet context found MIME=" + contentType);
        boolean acceptsGzip = false;
        String disposition = "inline";
        // To add new content types, add new mime-mapping entry in web.xml.
        if (contentType == null) {
            if (fileName.endsWith(".js")) {
                contentType = "application/javascript";
            }
            contentType = "application/octet-stream";
        }
        // the browser and expand content type with the one and right character encoding.
        if (contentType.startsWith("text")) {
            String acceptEncoding = request.getHeader("Accept-Encoding");
            acceptsGzip = acceptEncoding != null && accepts(acceptEncoding, "gzip");
            contentType += ";charset=UTF-8";
        } else // the browser, then set to inline, else attachment which will pop a 'save as' dialogue.
        if (!contentType.startsWith("image")) {
            String accept = request.getHeader("Accept");
            disposition = accept != null && accepts(accept, contentType) ? "inline" : "attachment";
        }
        log.info(" content-type:" + contentType);
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

    // Helpers (can be refactored to public utility class) ----------------------------------------
    /**
     * Returns true if the given accept header accepts the given value.
     *
     * @param acceptHeader The accept header.
     * @param toAccept The value to be accepted.
     * @return True if the given accept header accepts the given value.
     */
    private static boolean accepts(String acceptHeader, String toAccept) {
        String[] acceptValues = acceptHeader.split("\\s*(,|;)\\s*");
        Arrays.sort(acceptValues);
        return Arrays.binarySearch(acceptValues, toAccept) > -1 || Arrays.binarySearch(acceptValues, toAccept.replaceAll("/.*$", "/*")) > -1 || Arrays.binarySearch(acceptValues, "*/*") > -1;
    }

    /**
     * Returns true if the given match header matches the given value.
     *
     * @param matchHeader The match header.
     * @param toMatch The value to be matched.
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
        Map<String, EntityReferenceWrapper> webpages = (Map<String, EntityReferenceWrapper>) app.getCfValues().getCfValue("webPages").getMapValue();
        EntityReferenceWrapper webpage = null;
        if (webpages != null && webpages.containsKey(remainingPath)) {
            webpage = webpages.get(remainingPath);
        }
        if (webpage == null) {
            return docType + "<html><body><h1>Page " + remainingPath + " not found among " + app.getCfValues().getValues() + "</h1></body></html>";
        }
        StringBuilder result = new StringBuilder();
        result.append(docType).append(ln);
        result.append("<html>").append(ln);
        result.append("<head>").append(ln);
        if (app.getCfValues().getCfValue("stylesheets") != null) {
            Map<String, EntityReferenceWrapper> stylesheets = (Map<String, EntityReferenceWrapper>) app.getCfValues().getCfValue("stylesheets").getListValue();
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
        CustomEntityInstance webpage = customEntityInstanceService.findByCodeByCet("ApplicationWebPage", webpageWrapper.getCode());
        result.append("<title>").append(webpage.getDescription()).append("</title>");
        if (webpage.getCfValues().getCfValue("stylesheets") != null) {
            Map<String, EntityReferenceWrapper> stylesheets = (Map<String, EntityReferenceWrapper>) webpage.getCfValues().getCfValue("stylesheets").getListValue();
            if (stylesheets != null && stylesheets.size() > 0) {
                result.append(getStyleSheets(stylesheets));
            }
        }
        result.append("</head>").append(ln);
        if (webpage.getCfValues().getCfValue("body") != null) {
            result.append("<body>").append(ln).append(webpage.getCfValues().getCfValue("body").getStringValue()).append("</body>").append(ln);
        }
        return result.toString();
    }

    static String getStyleSheets(Map<String, EntityReferenceWrapper> stylesheets) {
        StringBuilder result = new StringBuilder();
        for (EntityReferenceWrapper stylesheetWrapper : stylesheets.values()) {
            CustomEntityInstance stylesheet = customEntityInstanceService.findByCodeByCet("CSSStyleSheet", stylesheetWrapper.getCode());
            if (stylesheet.getCfValues().getCfValue("externalURL") != null) {
                result.append("<link rel=\"stylesheet\" href=\"").append(stylesheet.getCfValues().getCfValue("externalURL").getStringValue()).append("\">").append(ln);
            } else {
                result.append("<style>").append(stylesheet.getCfValues().getCfValue("content").getStringValue()).append("</style>").append(ln);
            }
        }
        return result.toString();
    }
}
