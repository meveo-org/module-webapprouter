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

    private static final int DEFAULT_BUFFER_SIZE = 10240; // ..bytes = 10KB.
    private static final long DEFAULT_EXPIRE_TIME = 604800000L; // ..ms = 1 week.
    private static final String MULTIPART_BOUNDARY = "MULTIPART_BYTERANGES";
    private static final Logger log = LoggerFactory.getLogger(WebApp.class);

    private Object result = "";
    private String appCode = "";

    CustomEntityInstanceService customEntityInstanceService;

    public WebApp() {
        customEntityInstanceService = (CustomEntityInstanceService) getServiceInterface("CustomEntityInstanceService");
    }

    @Override
    public void execute(Map<String, Object> methodContext) {
        EndpointRequest request = (EndpointRequest) methodContext.get("request");
        EndpointResponse response = (EndpointResponse) methodContext.get("response");
        String remainingPath = request.getRemainingPath();
        File file = null;
        try {
            ParamBeanFactory paramBeanFactory = (ParamBeanFactory) EjbUtils.getServiceInterface(ParamBeanFactory.class.getSimpleName());
            ParamBean config = paramBeanFactory.getInstance();
            String basePath = config.getProperty("providers.rootDir", "." + File.separator + "meveodata");
            String rootDirectory = config.getProperty("provider.rootDir", "default");
            File dir = new File(basePath);
            if (!dir.exists() || !dir.isDirectory() || !dir.canRead()) {
                response.setStatus(400);
                response.setErrorMessage("Unable to locate " + remainingPath);
                return;
            }
            basePath += File.separator + rootDirectory + File.separator + "webapp" + File.separator;
            log.debug("basePath: {}", basePath);

            File path = new File(basePath);
            if (!path.exists()) {
                path.mkdirs();
            }
            if (!path.isDirectory()) {
                response.setStatus(400);
                response.setErrorMessage(basePath + " is not a directory.");
                return;
            } else if (!path.canRead()) {
                response.setStatus(400);
                response.setErrorMessage(basePath + " is not readable.");
                return;
            }

            log.debug("remainingPath: " + remainingPath);
            if (remainingPath.equalsIgnoreCase("/" + appCode)) {
                file = new File(basePath, URLDecoder.decode(remainingPath, "UTF-8"));
            } else {
                file = new File(basePath + File.separator + appCode, URLDecoder.decode(remainingPath, "UTF-8"));
            }

            log.debug("appCode: " + appCode);
            log.debug("fileName: " + file.getAbsolutePath());
            log.debug("isFile: " + file.isFile());
            log.debug("isDirectory: " + file.isDirectory());
            // Check if file actually exists in filesystem.
            if (!file.exists() || file.isDirectory()) {
                if (file.isDirectory()) {
                    int subFolderIndex = remainingPath.indexOf("/", 1);
                    String pathParameters = subFolderIndex > -1 ? remainingPath.substring(0, subFolderIndex) : remainingPath;
                    file = new File(basePath, URLDecoder.decode(pathParameters + File.separator + "index.html", "UTF-8"));
                }

                // default to root index.html
                if (!file.exists()) {
                    String indexFile = basePath + File.separator + appCode + File.separator + "index.html";
                    file = new File(indexFile);
                }

                // check if the index.html inside the directory exists
                if(!file.exists()) {
                    // attempt to load from HtmlSerializer instead
                    CustomEntityInstance app = customEntityInstanceService.findByCodeByCet("Application", this.appCode);
                    result = org.manaty.webapp.HtmlApplicationSerializer.getHtml(app, remainingPath, customEntityInstanceService);
                    return;
                }
            }
        } catch (IOException ioException) {
            response.setStatus(400);
            result = "Encountered error while trying to load " + remainingPath;
            return;
        }

        retrieveFile(file, request, response);

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

    private void retrieveFile(File file, EndpointRequest request, EndpointResponse response) {
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
            response.setHeader("ETag", eTag); // Required in 304.
            response.setDateHeader("Expires", expires); // Postpone cache with 1 week.
            log.debug("If-None-Match");
            return;
        }

        // If-Modified-Since header should be greater than LastModified. If so, then return 304.
        // This header is ignored if any If-None-Match header is specified.
        long ifModifiedSince = request.getDateHeader("If-Modified-Since");
        if (ifNoneMatch == null && ifModifiedSince != -1 && ifModifiedSince + 1000 > lastModified) {
            response.setStatus(304);
            response.setHeader("ETag", eTag); // Required in 304.
            response.setDateHeader("Expires", expires); // Postpone cache with 1 week.
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

        // If content type is unknown, then set the default value.
        // For all content types, see: http://www.w3schools.com/media/media_mimeref.asp
        // To add new content types, add new mime-mapping entry in web.xml.
        if (contentType == null) {
            if (fileName.endsWith(".js")) {
                contentType = "application/javascript";
            }
            contentType = "application/octet-stream";
        }

        // If content type is text, then determine whether GZIP content encoding is supported by
        // the browser and expand content type with the one and right character encoding.
        if (contentType.startsWith("text")) {
            String acceptEncoding = request.getHeader("Accept-Encoding");
            acceptsGzip = acceptEncoding != null && accepts(acceptEncoding, "gzip");
            contentType += ";charset=UTF-8";
        }

        // Else, expect for images, determine content disposition. If content type is supported by
        // the browser, then set to inline, else attachment which will pop a 'save as' dialogue.
        else if (!contentType.startsWith("image")) {
            String accept = request.getHeader("Accept");
            disposition = accept != null && accepts(accept, contentType) ? "inline" : "attachment";
        }

        log.debug("before Initialize content-type:" + contentType);

        // Initialize response.
        response.setBufferSize(DEFAULT_BUFFER_SIZE);
        response.setContentType(contentType);
        response.setHeader("Content-Disposition", disposition + ";filename=\"" + fileName + "\"");
        response.setHeader("Accept-Ranges", "bytes");
        response.setHeader("ETag", eTag);
        response.setDateHeader("Last-Modified", lastModified);
        response.setDateHeader("Expires", expires);

        // Send requested file (part(s)) to client ------------------------------------------------

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
        return Arrays.binarySearch(acceptValues, toAccept) > -1 || Arrays.binarySearch(acceptValues, toAccept.replaceAll("/.*$", "/*")) > -1
                || Arrays.binarySearch(acceptValues, "*/*") > -1;
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

        Map<String, EntityReferenceWrapper> webpages = (Map<String, EntityReferenceWrapper>) app.getCfValues().getCfValue("webPages").getMapValue();
        EntityReferenceWrapper webpage = null;
        if (webpages != null && webpages.containsKey(remainingPath)) {
            webpage = webpages.get(remainingPath);
        }
        if (webpage == null) {
            return "<html><body><h1>Page "
                    + remainingPath
                    + " not found among "
                    + app.getCfValues().getValues()
                    + "</h1></body></html>";
        }

        StringBuilder result = new StringBuilder();
        result.append(docType).append(ln);
        result.append("<html>").append(ln);
        result.append("<head>").append(ln);
        if (app.getCfValues().getCfValue("stylesheets") != null) {
            Map<String, EntityReferenceWrapper> stylesheets =
                    (Map<String, EntityReferenceWrapper>) app.getCfValues().getCfValue("stylesheets").getListValue();
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
        CustomEntityInstance webpage = customEntityInstanceService
                .findByCodeByCet("ApplicationWebPage", webpageWrapper.getCode());
        result.append("<title>").append(webpage.getDescription()).append("</title>");
        if (webpage.getCfValues().getCfValue("stylesheets") != null) {
            Map<String, EntityReferenceWrapper> stylesheets =
                    (Map<String, EntityReferenceWrapper>) webpage.getCfValues().getCfValue("stylesheets").getListValue();
            if (stylesheets != null && stylesheets.size() > 0) {
                result.append(getStyleSheets(stylesheets));
            }
        }
        result.append("</head>").append(ln);

        if (webpage.getCfValues().getCfValue("body") != null) {
            result.append("<body>")
                    .append(ln)
                    .append(webpage.getCfValues().getCfValue("body").getStringValue())
                    .append("</body>")
                    .append(ln);
        }
        return result.toString();
    }

    static String getStyleSheets(Map<String, EntityReferenceWrapper> stylesheets) {
        StringBuilder result = new StringBuilder();
        for (EntityReferenceWrapper stylesheetWrapper : stylesheets.values()) {
            CustomEntityInstance stylesheet = customEntityInstanceService
                    .findByCodeByCet("CSSStyleSheet", stylesheetWrapper.getCode());
            if (stylesheet.getCfValues().getCfValue("externalURL") != null) {
                result
                        .append("<link rel=\"stylesheet\" href=\"")
                        .append(stylesheet.getCfValues().getCfValue("externalURL").getStringValue())
                        .append("\">")
                        .append(ln);
            } else {
                result.append("<style>")
                        .append(stylesheet.getCfValues().getCfValue("content").getStringValue())
                        .append("</style>")
                        .append(ln);
            }
        }
        return result.toString();

    }
}
