package org.ff4j.web.embedded;

import java.io.IOException;

/*
 * #%L AdministrationConsoleRenderer.java (ff4j-web) by Cedrick LUNVEN %% Copyright (C) 2013 Ff4J %% Licensed under the Apache
 * License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS"
 * BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language
 * governing permissions and limitations under the License. #L%
 */

import java.io.InputStream;
import java.io.PrintWriter;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.ff4j.FF4j;
import org.ff4j.core.Feature;
import org.ff4j.core.FlippingStrategy;
import org.ff4j.property.AbstractProperty;

/**
 * Used to build GUI Interface for feature flip servlet. It contains gui component render and parmeters
 * 
 * @author <a href="mailto:cedrick.lunven@gmail.com">Cedrick LUNVEN</a>
 */
public final class ConsoleRenderer implements ConsoleConstants {

    /** Cache for page blocks. */
    private static String htmlTemplate = null;

    /** Load CSS. */
    private static String cssContent = null;

    /** Load JS. */
    private static String jsContent = null;

    /** Cache for page blocks. */
    static final String TABLE_FEATURES_FOOTER = "" + "</tbody></table></form></fieldset>";

    /** fin de ligne. **/
    static final String END_OF_LINE = "\r\n";

    /** Get version of the component. */
    static final String FF4J_VERSION = ConsoleRenderer.class.getPackage().getImplementationVersion();

    /**
     * Render the ff4f console webpage through different block.
     * 
     * @param req
     *            http request (with parameters)
     * @param res
     *            http response (with outouput test)
     * @param message
     *            text in the information box (blue/green/orange/red)
     * @param messagetype
     *            type of informatice message (info,success,warning,error)
     * @throws IOException
     *             error during populating http response
     */
    static void renderPage(FF4j ff4j, HttpServletRequest req, HttpServletResponse res, String msg, String msgType) throws IOException {
        res.setContentType(CONTENT_TYPE_HTML);
        PrintWriter out = res.getWriter();

        // Header of the page
        String htmlContent = renderTemplate(req);

        // Subsctitution MESSAGE BOX
        htmlContent = htmlContent.replaceAll("\\{" + KEY_ALERT_MESSAGE + "\\}", renderMessageBox(msg, msgType));

        // Subsctitution FEATURE_ROWS
        htmlContent = htmlContent.replaceAll("\\{" + KEY_FEATURE_ROWS + "\\}", renderFeatureRows(ff4j, req));
        
        // substitution PROPERTIES_ROWS
        htmlContent = htmlContent.replaceAll("\\{" + KEY_PROPERTIES_ROWS + "\\}", renderPropertiesRows(ff4j, req));
        
        // Substitution GROUP_LIST
        String groups = ConsoleRenderer.renderGroupList(ff4j, MODAL_EDIT);
        htmlContent = htmlContent.replaceAll("\\{" + KEY_GROUP_LIST_EDIT + "\\}", groups);
        groups = groups.replaceAll(MODAL_EDIT, MODAL_CREATE);
        htmlContent = htmlContent.replaceAll("\\{" + KEY_GROUP_LIST_CREATE + "\\}", groups);
        groups = groups.replaceAll(MODAL_CREATE, MODAL_TOGGLE);
        htmlContent = htmlContent.replaceAll("\\{" + KEY_GROUP_LIST_TOGGLE + "\\}", groups);

        // Substitution PERMISSIONS
        final String permissions = renderPermissionList(ff4j);
        htmlContent = htmlContent.replaceAll("\\{" + KEY_PERMISSIONLIST + "\\}", permissions);

        out.println(htmlContent);
    }


    /**
     * Build info messages.
     * 
     * @param featureName
     *            target feature name
     * @param operationd
     *            target operationId
     * @return
     */
    static String msg(String featureName, String operationId) {
        return String.format("Feature <b>%s</b> has been successfully %s", featureName, operationId);
    }
    
    /**
     * Build info messages.
     * 
     * @param featureName
     *            target feature name
     * @param operationd
     *            target operationId
     * @return
     */
    static  String renderMsgProperty(String featureName, String operationId) {
        return String.format("Property <b>%s</b> has been successfully %s", featureName, operationId);
    }

    /**
     * Build info messages.
     * 
     * @param featureName
     *            target feature name
     * @param operationd
     *            target operationId
     * @return
     */
    static String renderMsgGroup(String groupName, String operationId) {
        return String.format("Group <b>%s</b> has been successfully %s", groupName, operationId);
    }
    
    /**
     * Deliver CSS and Javascript files/
     * 
     * @param req
     *            request
     * @param res
     *            response
     * @return value for resources
     * @throws IOException
     *             exceptions
     */
     static boolean renderResources(HttpServletRequest req, HttpServletResponse res) throws IOException {
        // Serve static resource file as CSS and Javascript
        String resources = req.getParameter(RESOURCE);
        if (resources != null && !resources.isEmpty()) {
            if (RESOURCE_CSS_PARAM.equalsIgnoreCase(resources)) {
                res.setContentType(CONTENT_TYPE_CSS);
                res.getWriter().println(ConsoleRenderer.getCSS());
                return true;
            } else if (RESOURCE_JS_PARAM.equalsIgnoreCase(resources)) {
                res.setContentType(CONTENT_TYPE_JS);
                res.getWriter().println(ConsoleRenderer.getJS());
                return true;
            }
        }
        return false;
    }
    
    /**
     * Load HTML template file and substitute by current URL context path
     * 
     * @param req
     *            current http request
     * @return current text part as string
     */
    static final String renderTemplate(HttpServletRequest req) {
        if (htmlTemplate == null || htmlTemplate.isEmpty()) {
            String ctx = req.getContextPath() + req.getServletPath() + "";
            htmlTemplate = loadFileAsString(TEMPLATE_FILE);
            htmlTemplate = htmlTemplate.replaceAll("\\{" + KEY_SERVLET_CONTEXT + "\\}", ctx);
            htmlTemplate = htmlTemplate.replaceAll("\\{" + KEY_VERSION + "\\}", FF4J_VERSION);
        }
        return htmlTemplate;
    }
    
    static final String renderPropertiesRows(FF4j ff4j, HttpServletRequest req) {
        StringBuilder sb = new StringBuilder();
        final Map < String, AbstractProperty<?>> mapOfProperties = ff4j.getProperties();
        for(String uid : mapOfProperties.keySet()) {
            AbstractProperty<?> currentProperty = mapOfProperties.get(uid);
            sb.append("<tr>" + END_OF_LINE);
            
            // Column with uid and description as tooltip
            sb.append("<td><a class=\"ff4j-properties\" ");
            if (null != currentProperty.getDescription()) {
                sb.append(" tooltip=\"");
                sb.append(currentProperty.getDescription());
                sb.append("\"");
            }
            sb.append(">");
            sb.append(currentProperty.getName());
            sb.append("</a>");
           
            // Colonne Value
            sb.append("</td><td>");
            if (null != currentProperty.asString()) {
                sb.append(currentProperty.asString());
            } else {
                sb.append("--");
            }
            
            // Colonne Type
            sb.append("</td><td>");
            sb.append(currentProperty.getType());
            
            // Colonne Fixed Value
            sb.append("</td><td>");
            if (null != currentProperty.getFixedValues()) {
                for (Object o : currentProperty.getFixedValues()) {
                    sb.append("<li>" + o.toString());
                }
            } else {
                sb.append("--");
            }
            
            // Colonne Button Edit
            sb.append("</td><td style=\"width:5%;text-align:center\">");
            sb.append("<a data-toggle=\"modal\" href=\"#modalEditProperty\" data-pname=\"" + currentProperty.getName() + "\" ");
            sb.append(" style=\"width:6px;\" class=\"open-EditPropertyDialog btn\">");
            sb.append("<i class=\"icon-pencil\" style=\"margin-left:-5px;\"></i></a>");

            // Colonne Button Delete
            sb.append("</td><td style=\"width:5%;text-align:center\">");
            sb.append("<a href=\"");
            sb.append(req.getContextPath());
            sb.append(req.getServletPath());
            sb.append("?op=" + OP_RMV_PROPERTY + "&" + FEATID + "=" + currentProperty.getName());
            sb.append("\" style=\"width:6px;\" class=\"btn\">");
            sb.append("<i class=\"icon-trash\" style=\"margin-left:-5px;\"></i>");
            sb.append("</a>");
            sb.append("</td></tr>");
        }
        return sb.toString();
    }

    /**
     * Produce the rows of the Feature Table.
     *
     * @param currentElement
     * @return
     */
    static final String renderFeatureRows(FF4j ff4j, HttpServletRequest req) {
        StringBuilder sb = new StringBuilder();
        final Map < String, Feature> mapOfFeatures = ff4j.getFeatures();
        for(String uid : mapOfFeatures.keySet()) {
            Feature currentFeature = mapOfFeatures.get(uid);
            sb.append("<tr>" + END_OF_LINE);
            
            // Column with uid and description as tooltip
            sb.append("<td><a class=\"ff4j-tooltip\" ");
            if (null != currentFeature.getDescription()) {
                sb.append(" tooltip=\"");
                sb.append(currentFeature.getDescription());
                sb.append("\"");
            }
            sb.append(">");
            sb.append(currentFeature.getUid());
            sb.append("</a>");
            
            // Colonne Group
            sb.append("</td><td>");
            if (null != currentFeature.getGroup()) {
                sb.append(currentFeature.getGroup());
            } else {
                sb.append("--");
            }
            
            // Colonne Permissions
            sb.append("</td><td>");
            Set < String > permissions = currentFeature.getPermissions();
            if (null != permissions && !permissions.isEmpty()) {
                boolean first = true;
                for (String perm : permissions) {
                    if (!first) {
                        sb.append(",");
                    }
                    sb.append(perm);
                    first = false;
                }
            } else {
                sb.append("--");
            }
            
            // Colonne Strategy
            sb.append("</td><td style=\"word-break: break-all;\">");
            FlippingStrategy fs = currentFeature.getFlippingStrategy();
            if (null != fs) {
                // Escape $ caracter within className
                sb.append(fs.getClass().getCanonicalName().replaceAll("\\$", "_"));
                String initParams = "<br/>&nbsp;" + fs.getInitParams();
                sb.append(initParams.replaceAll("\\$", "_"));
            } else {
                sb.append("--");
            }
            
            // Colonne 'Holy' Toggle
            sb.append("</td><td style=\"width:8%;text-align:center\">");
            sb.append("<label class=\"switch switch-green\">");
            sb.append("<input id=\"" + currentFeature.getUid() + "\" type=\"checkbox\" class=\"switch-input\"");
            sb.append(" onclick=\"javascript:toggle(this)\" ");
            if (currentFeature.isEnable()) {
                sb.append(" checked");
            }
            sb.append(">");
            sb.append("<span class=\"switch-label\" data-on=\"On\" data-off=\"Off\"></span>");
            sb.append("<span class=\"switch-handle\"></span>");
            sb.append("</label>");
            
            // Colonne Button Edit
            sb.append("</td><td style=\"width:5%;text-align:center\">");
            sb.append("<a data-toggle=\"modal\" href=\"#modalEdit\" data-id=\"" + currentFeature.getUid() + "\" ");
            sb.append(" data-desc=\"" + currentFeature.getDescription() + "\"");
            sb.append(" data-group=\"" + currentFeature.getGroup() + "\"");
            sb.append(" data-strategy=\"");
            if (null != currentFeature.getFlippingStrategy()) {
                sb.append(currentFeature.getFlippingStrategy().getClass().getCanonicalName());
            }
            sb.append("\" data-stratparams=\"");
            if (null != currentFeature.getFlippingStrategy()) {
                sb.append(currentFeature.getFlippingStrategy().getInitParams());
            }
            sb.append("\" data-permissions=\"");
            if (null != currentFeature.getPermissions() && !currentFeature.getPermissions().isEmpty()) {
                sb.append(currentFeature.getPermissions());
            }
            sb.append("\" style=\"width:6px;\" class=\"open-EditFlipDialog btn\">");
            sb.append("<i class=\"icon-pencil\" style=\"margin-left:-5px;\"></i></a>");

            // Colonne Button Delete
            sb.append("</td><td style=\"width:5%;text-align:center\">");
            sb.append("<a href=\"");
            sb.append(req.getContextPath());
            sb.append(req.getServletPath());
            sb.append("?op=" + OP_RMV_FEATURE + "&" + FEATID + "=" + uid);
            sb.append("\" style=\"width:6px;\" class=\"btn\">");
            sb.append("<i class=\"icon-trash\" style=\"margin-left:-5px;\"></i>");
            sb.append("</a>");
            sb.append("</td></tr>");
        }
        return sb.toString();
    }
    
    /**
     * Display message box if message.
     * 
     * @param message
     *            target message to display
     * @param type
     *            type of messages
     * @return html content to be displayed as message
     */
    static String renderMessageBox(String message, String type) {
        StringBuilder sb = new StringBuilder();
        // Display Message box
        if (message != null && !message.isEmpty()) {
            sb.append("<div class=\"alert alert-" + type + "\" >");
            sb.append("<button type=\"button\" class=\"close\" data-dismiss=\"alert\">&times;</button>");
            sb.append("<span style=\"font-style:normal;color:#696969;\">");
            sb.append(message);
            sb.append("</span>");
            sb.append("</div>");
        }
        return sb.toString();
    }

    /**
     * Render group list block.
     * 
     * @param ff4j
     *            target ff4j.
     * @return list of group
     */
    static String renderGroupList(FF4j ff4j, String modalId) {
        StringBuilder sb = new StringBuilder();
        if (null != ff4j.getFeatureStore().readAllGroups()) {
            for (String group : ff4j.getFeatureStore().readAllGroups()) {
                sb.append("<li><a href=\"javascript:\\$('\\#" + modalId + " \\#groupName').val('");
                sb.append(group);
                sb.append("');\">");
                sb.append(group);
                sb.append("</a></li>");
            }
        }
        return sb.toString();
    }

    /**
     * Render a permission list.
     *
     * @param ff4j
     *            reference to curent ff4j instance
     * @return string representing the list of permissions
     */
    static String renderPermissionList(FF4j ff4j) {
        StringBuilder sb = new StringBuilder("<br/>");
        if (null != ff4j.getAuthorizationsManager()) {
            for (String permission : ff4j.getAuthorizationsManager().listAllPermissions()) {
                sb.append("\r\n<br/>&nbsp;&nbsp;&nbsp;<input type=\"checkbox\" ");
                sb.append(" name=\"" + PREFIX_CHECKBOX + permission + "\"");
                sb.append(" id=\"" + PREFIX_CHECKBOX + permission + "\" >&nbsp;");
                sb.append(permission);
            }
        }
        return sb.toString();
    }

    /**
     * Load the CSS File As String.
     *
     * @return CSS File
     */
    static final String getCSS() {
        if (null == cssContent) {
            cssContent = loadFileAsString(RESOURCE_CSS_FILE);
        }
        return cssContent;
    }

    /**
     * Load the JS File As String.
     *
     * @return JS File
     */
    static final String getJS() {
        if (null == jsContent) {
            jsContent = loadFileAsString(RESOURCE_JS_FILE);
        }
        return jsContent;
    }

    /**
     * Utils method to load a file as String.
     *
     * @param fileName
     *            target file Name.
     * @return target file content as String
     */
    private static String loadFileAsString(String fileName) {
        InputStream in = ConsoleRenderer.class.getClassLoader().getResourceAsStream(fileName);
        if (in == null) {
            throw new IllegalArgumentException("Cannot load file " + fileName + " from classpath");
        }
        Scanner currentScan = null;
        StringBuilder strBuilder = new StringBuilder();
        try {
            currentScan = new Scanner(in, UTF8_ENCODING);
            while (currentScan.hasNextLine()) {
                strBuilder.append(currentScan.nextLine());
                strBuilder.append(NEW_LINE);
            }
        } finally {
            if (currentScan != null) {
                currentScan.close();
            }
        }
        return strBuilder.toString();
    }

}
