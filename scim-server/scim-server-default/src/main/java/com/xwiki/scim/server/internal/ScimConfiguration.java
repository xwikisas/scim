/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package com.xwiki.scim.server.internal;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

import javax.inject.Singleton;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.xwiki.component.annotation.Component;
import org.xwiki.configuration.ConfigurationSource;
import org.xwiki.contrib.usercommon.formatter.UserFormatterFactory;

import com.xpn.xwiki.XWiki;
import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.BaseObject;

/**
 * The SCIM configuration.
 * @version $Id$
 * @since 1.0
 */
@Component (roles = ScimConfiguration.class)
@Singleton
public class ScimConfiguration
{
    @Inject
    private Provider<XWikiContext> contextProvider;

    @Inject
    private ConfigurationSource configuration;

    @Inject
    private Logger logger;

    /**
     * @return the list of auth schemes that must be advertised in the server configuration SCIM endpoint.
     */
    public Collection<String> getAdvertisedAuthSchemes()
    {
        BaseObject conf = getConfFromDoc();
        if (conf == null) {
            logger.error("Failed to get the SCIM configuration document, no authentication schemes will be advertised");
            return Collections.emptyList();
        }

        return getListConf("advertisedAuthSchemes", "scim.advertisedAuthSchemes");
    }

    /**
     * @return whether OIDC objects should be created when creating users from SCIM and whether to allow SCIM clients
     * to manage users created from OIDC.
     */
    public boolean isOIDCEnabled()
    {
        return getBoolConf("oidcEnabled", "scim.oidc.enabled",
            "OIDC interoperability is considered disabled", false);
    }

    private boolean isUsernameCleaningEnabled()
    {
        return getBoolConf("usernameCleaningEnabled", "scim.usernameCleaningEnabled",
            "user and group name cleaning will be considered enabled", false);
    }

    private boolean getBoolConf(String docConfField, String propertiesConfField, String msg, boolean def)
    {
        BaseObject conf = getConfFromDoc();
        if (conf == null) {
            logger.error("Failed to get the SCIM configuration document, {}", msg);
            return def;
        }

        int enabled = conf.getIntValue(docConfField, -1);
        if (enabled == -1) {
            String enabledStr = configuration.getProperty(propertiesConfField);
            return StringUtils.isNotEmpty(enabledStr) && !"0".equals(enabledStr);
        }

        return enabled != 0;
    }

    /**
     * @return the string that must be put in the issuer field of OIDC UserClass objects.
     */
    public String getOIDCIssuer()
    {
        return StringUtils.defaultString(getConf("oidcIssuer", "scim.oidc.issuer"));
    }

    /**
     * @return the format that must be user to fill the subject field of OIDC UserClass objects.
     */
    public String getOIDCSubject()
    {
        return StringUtils.defaultString(getConf("oidcSubject", "scim.oidc.subject"));
    }

    /**
     * @return the users allowed to use the SCIM rest APIs
     */
    public String[] getScimUsers()
    {
        String usersStr = StringUtils.defaultString(getConf("users", "scim.users"));
        return StringUtils.split(usersStr, ',');
    }

    /**
     * @return the format to use for usernames.
     */
    public String getUserNameFormat()
    {
        return getConf("userNameFormat", "scim.userNameFormat");
    }

    /**
     * @return the format to use for groups.
     */
    public String getGroupNameFormat()
    {
        return getConf("groupNameFormat", "scim.groupNameFormat");
    }

    /**
     * @return the pattern for forbidden characters in user and group names
     */
    public Pattern getForbiddenPattern()
    {
        if (!isUsernameCleaningEnabled()) {
            return null;
        }

        String pattern = getConf("forbiddenPattern", "scim.forbiddenPattern");
        return StringUtils.isEmpty(pattern) ? UserFormatterFactory.DEFAULT_FORBIDDEN_PATTERN : Pattern.compile(pattern);
    }

    /**
     * @return the forbidden replacement for forbidden characters
     */
    public String getForbiddenReplacement()
    {
        String forbiddenReplacement = getConf("forbiddenReplacement", "scim.forbiddenReplacement");
        return forbiddenReplacement == null ? UserFormatterFactory.DEFAULT_FORBIDDEN_REPLACEMENT : forbiddenReplacement;
    }

    private BaseObject getConfFromDoc()
    {
        XWikiContext context = contextProvider.get();
        XWiki wiki = context.getWiki();
        try {
            XWikiDocument confDoc = wiki.getDocument(Constants.SCIM_CONF_REF, context);
            return confDoc.getXObject(Constants.SCIM_CONF_CLASS_REF, true, context);
        } catch (XWikiException e) {
            logger.error("Failed to get the SCIM configuration document", e);
            return null;
        }
    }

    private String getConf(String docConfField, String propertiesConfField)
    {
        BaseObject conf = getConfFromDoc();
        if (conf == null) {
            return null;
        }
        String v = conf.getStringValue(docConfField);

        if (StringUtils.isEmpty(v)) {
            v = configuration.getProperty(propertiesConfField);
        }

        return v;
    }

    private Collection<String> getListConf(String docConfField, String propertiesConfField)
    {
        BaseObject conf = getConfFromDoc();
        if (conf == null) {
            return List.of();
        }
        List<String> v = conf.getListValue(docConfField);

        if (CollectionUtils.isEmpty(v)) {
            v = configuration.getProperty(propertiesConfField);
        }

        return v;
    }
}
