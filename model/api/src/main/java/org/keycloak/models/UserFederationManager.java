package org.keycloak.models;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author <a href="mailto:bill@burkecentral.com">Bill Burke</a>
 * @version $Revision: 1 $
 */
public class UserFederationManager implements UserProvider {
    protected KeycloakSession session;

    public UserFederationManager(KeycloakSession session) {
        this.session = session;
    }

    @Override
    public UserModel addUser(RealmModel realm, String id, String username, boolean addDefaultRoles) {
        UserModel user = session.userStorage().addUser(realm, id, username, addDefaultRoles);
        for (UserFederationProviderModel federation : realm.getFederationProviders()) {
            UserFederationProvider fed = session.getProvider(UserFederationProvider.class, federation.getProviderName());
            return fed.addUser(realm, user);
        }
        return user;
    }

    protected UserFederationProvider getFederationProvider(UserFederationProviderModel model) {
        UserFederationProviderFactory factory = (UserFederationProviderFactory)session.getKeycloakSessionFactory().getProviderFactory(UserFederationProvider.class, model.getProviderName());
        return factory.getInstance(session, model);

    }

    @Override
    public UserModel addUser(RealmModel realm, String username) {
        UserModel user = session.userStorage().addUser(realm, username);
        for (UserFederationProviderModel federation : realm.getFederationProviders()) {
            UserFederationProvider fed = getFederationProvider(federation);
            return fed.addUser(realm, user);
        }
        return user;
    }

    protected UserFederationProvider getFederationLink(RealmModel realm, UserModel user) {
        if (user.getFederationLink() == null) return null;
        for (UserFederationProviderModel fed : realm.getFederationProviders()) {
            if (fed.getId().equals(user.getFederationLink())) {
                return getFederationProvider(fed);
            }
        }
        return null;
    }

    @Override
    public boolean removeUser(RealmModel realm, String name) {
        UserModel user = session.userStorage().getUserByUsername(name, realm);
        if (user == null) return false;
        UserFederationProvider link = getFederationLink(realm, user);
        if (link != null) {
            return link.removeUser(realm, user);
        }
        return session.userStorage().removeUser(realm, name);

    }

    @Override
    public void addSocialLink(RealmModel realm, UserModel user, SocialLinkModel socialLink) {
        UserFederationProvider link = getFederationLink(realm, user);
        if (link != null) {
            link.addSocialLink(realm, user, socialLink);
            return;
        }
        session.userStorage().addSocialLink(realm, user, socialLink);

    }

    @Override
    public boolean removeSocialLink(RealmModel realm, UserModel user, String socialProvider) {
        UserFederationProvider link = getFederationLink(realm, user);
        if (link != null) {
            return link.removeSocialLink(realm, user, socialProvider);
        }
        return session.userStorage().removeSocialLink(realm, user, socialProvider);
    }

    @Override
    public UserModel getUserById(String id, RealmModel realm) {
        UserModel user = session.userStorage().getUserById(id, realm);
        if (user != null) {
            UserFederationProvider link = getFederationLink(realm, user);
            if (link != null) {
                return link.proxy(user);
            }
            return user;
        }
        for (UserFederationProviderModel federation : realm.getFederationProviders()) {
            UserFederationProvider fed = getFederationProvider(federation);
            user = fed.getUserById(id, realm);
            if (user != null) return user;
        }
        return user;
    }

    @Override
    public UserModel getUserByUsername(String username, RealmModel realm) {
        UserModel user = session.userStorage().getUserByUsername(username, realm);
        if (user != null) {
            UserFederationProvider link = getFederationLink(realm, user);
            if (link != null) {
                return link.proxy(user);
            }
            return user;
        }
        for (UserFederationProviderModel federation : realm.getFederationProviders()) {
            UserFederationProvider fed = getFederationProvider(federation);
            user = fed.getUserByUsername(username, realm);
            if (user != null) return user;
        }
        return user;
    }

    @Override
    public UserModel getUserByEmail(String email, RealmModel realm) {
        UserModel user = session.userStorage().getUserByEmail(email, realm);
        if (user != null) {
            UserFederationProvider link = getFederationLink(realm, user);
            if (link != null) {
                return link.proxy(user);
            }
            return user;
        }
        for (UserFederationProviderModel federation : realm.getFederationProviders()) {
            UserFederationProvider fed = getFederationProvider(federation);
            user = fed.getUserByEmail(email, realm);
            if (user != null) return user;
        }
        return user;
    }

    @Override
    public UserModel getUserBySocialLink(SocialLinkModel socialLink, RealmModel realm) {
        UserModel user = session.userStorage().getUserBySocialLink(socialLink, realm);
        if (user != null) {
            UserFederationProvider link = getFederationLink(realm, user);
            if (link != null) {
                return link.proxy(user);
            }
            return user;
        }
        for (UserFederationProviderModel federation : realm.getFederationProviders()) {
            UserFederationProvider fed = getFederationProvider(federation);
            user = fed.getUserBySocialLink(socialLink, realm);
            if (user != null) return user;
        }
        return user;
    }

    @Override
    public List<UserModel> getUsers(RealmModel realm) {
        return getUsers(realm, 0, Integer.MAX_VALUE);

    }

    @Override
    public int getUsersCount(RealmModel realm) {
        return session.userStorage().getUsersCount(realm);
    }

    @Override
    public List<UserModel> getUsers(RealmModel realm, int firstResult, int maxResults) {
        Map<String, UserModel> users = new HashMap<String, UserModel>();
        List<UserModel> query = session.userStorage().getUsers(realm, firstResult, maxResults);
        for (UserModel user : query) {
            UserFederationProvider link = getFederationLink(realm, user);
            if (link != null) {
                users.put(user.getUsername(), link.proxy(user));
            } else {
                users.put(user.getUsername(), user);
            }
        }
        if (users.size() >= maxResults) {
            List<UserModel> results = new ArrayList<UserModel>(users.size());
            results.addAll(users.values());
            return results;
        }
        List<UserFederationProviderModel> federationProviders = realm.getFederationProviders();
        for (int i = federationProviders.size() - 1; i >= 0; i--) {
            UserFederationProviderModel federation = federationProviders.get(i);
            UserFederationProvider fed = getFederationProvider(federation);
            query = fed.getUsers(realm, firstResult, maxResults);
            for (UserModel user : query) users.put(user.getUsername(), user);
        }
        List<UserModel> results = new ArrayList<UserModel>(users.size());
        results.addAll(users.values());
        return results;
    }

    @Override
    public List<UserModel> searchForUser(String search, RealmModel realm) {
        return searchForUser(search, realm, 0, Integer.MAX_VALUE);
    }

    @Override
    public List<UserModel> searchForUser(String search, RealmModel realm, int firstResult, int maxResults) {
        Map<String, UserModel> users = new HashMap<String, UserModel>();
        List<UserModel> query = session.userStorage().searchForUser(search, realm, firstResult, maxResults);
        for (UserModel user : query) {
            UserFederationProvider link = getFederationLink(realm, user);
            if (link != null) {
                users.put(user.getUsername(), link.proxy(user));
            } else {
                users.put(user.getUsername(), user);
            }
        }
        if (users.size() >= maxResults) {
            List<UserModel> results = new ArrayList<UserModel>(users.size());
            results.addAll(users.values());
            return results;
        }
        List<UserFederationProviderModel> federationProviders = realm.getFederationProviders();
        for (int i = federationProviders.size() - 1; i >= 0; i--) {
            UserFederationProviderModel federation = federationProviders.get(i);
            UserFederationProvider fed = getFederationProvider(federation);
            query = fed.searchForUser(search, realm, firstResult, maxResults);
            for (UserModel user : query) users.put(user.getUsername(), user);
        }
        List<UserModel> results = new ArrayList<UserModel>(users.size());
        results.addAll(users.values());
        return results;
    }

    @Override
    public List<UserModel> searchForUserByAttributes(Map<String, String> attributes, RealmModel realm) {
        return searchForUserByAttributes(attributes, realm, 0, Integer.MAX_VALUE);
    }

    @Override
    public List<UserModel> searchForUserByAttributes(Map<String, String> attributes, RealmModel realm, int firstResult, int maxResults) {
        Map<String, UserModel> users = new HashMap<String, UserModel>();
        List<UserModel> query = session.userStorage().searchForUserByAttributes(attributes, realm, firstResult, maxResults);
        for (UserModel user : query) {
            UserFederationProvider link = getFederationLink(realm, user);
            if (link != null) {
                users.put(user.getUsername(), link.proxy(user));
            } else {
                users.put(user.getUsername(), user);
            }
        }
        if (users.size() >= maxResults) {
            List<UserModel> results = new ArrayList<UserModel>(users.size());
            results.addAll(users.values());
            return results;
        }
        List<UserFederationProviderModel> federationProviders = realm.getFederationProviders();
        for (int i = federationProviders.size() - 1; i >= 0; i--) {
            UserFederationProviderModel federation = federationProviders.get(i);
            UserFederationProvider fed = getFederationProvider(federation);
            query = fed.searchForUserByAttributes(attributes, realm, firstResult, maxResults);
            for (UserModel user : query) users.put(user.getUsername(), user);
        }
        List<UserModel> results = new ArrayList<UserModel>(users.size());
        results.addAll(users.values());
        return results;
    }

    @Override
    public Set<SocialLinkModel> getSocialLinks(UserModel user, RealmModel realm) {
        UserFederationProvider link = getFederationLink(realm, user);
        if (link != null) {
            return link.getSocialLinks(user, realm);
        }
        return session.userStorage().getSocialLinks(user, realm);
    }

    @Override
    public SocialLinkModel getSocialLink(UserModel user, String socialProvider, RealmModel realm) {
        UserFederationProvider link = getFederationLink(realm, user);
        if (link != null) {
            return link.getSocialLink(user, socialProvider, realm);
        }
        return session.userStorage().getSocialLink(user, socialProvider, realm);
    }

    @Override
    public void preRemove(RealmModel realm) {
        for (UserFederationProviderModel federation : realm.getFederationProviders()) {
            UserFederationProvider fed = getFederationProvider(federation);
            fed.preRemove(realm);
        }
        session.userStorage().preRemove(realm);
    }

    @Override
    public void preRemove(RealmModel realm, RoleModel role) {
        for (UserFederationProviderModel federation : realm.getFederationProviders()) {
            UserFederationProvider fed = getFederationProvider(federation);
            fed.preRemove(realm, role);
        }
        session.userStorage().preRemove(realm, role);
    }

    @Override
    public boolean validCredentials(RealmModel realm, UserModel user, List<UserCredentialModel> input) {
        UserFederationProvider link = getFederationLink(realm, user);
        if (link != null) {
            if (link.getSupportedCredentialTypes().size() > 0) {
                List<UserCredentialModel> fedCreds = new ArrayList<UserCredentialModel>();
                List<UserCredentialModel> localCreds = new ArrayList<UserCredentialModel>();
                for (UserCredentialModel cred : input) {
                    if (fedCreds.contains(cred.getType())) {
                        fedCreds.add(cred);
                    } else {
                        localCreds.add(cred);
                    }
                }
                if (!link.validCredentials(realm, user, fedCreds)) {
                    return false;
                }
                return session.userStorage().validCredentials(realm, user, localCreds);
            }
        }
        return session.userStorage().validCredentials(realm, user, input);
    }

    @Override
    public boolean validCredentials(RealmModel realm, UserModel user, UserCredentialModel... input) {
        UserFederationProvider link = getFederationLink(realm, user);
        if (link != null) {
            Set<String> supportedCredentialTypes = link.getSupportedCredentialTypes();
            if (supportedCredentialTypes.size() > 0) {
                List<UserCredentialModel> fedCreds = new ArrayList<UserCredentialModel>();
                List<UserCredentialModel> localCreds = new ArrayList<UserCredentialModel>();
                for (UserCredentialModel cred : input) {
                    if (supportedCredentialTypes.contains(cred.getType())) {
                        fedCreds.add(cred);
                    } else {
                        localCreds.add(cred);
                    }
                }
                if (!link.validCredentials(realm, user, fedCreds)) {
                    return false;
                }
                return session.userStorage().validCredentials(realm, user, localCreds);
            }
        }
        return session.userStorage().validCredentials(realm, user, input);
    }

    @Override
    public void close() {
    }
}