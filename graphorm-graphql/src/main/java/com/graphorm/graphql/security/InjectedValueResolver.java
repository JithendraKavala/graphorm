package com.graphorm.graphql.security;

import com.graphorm.core.metadata.FieldMetadata;
import graphql.GraphQLContext;
import graphql.schema.DataFetchingEnvironment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;

/**
 * Resolves values for backend-injected fields ({@code @injected(from: "...")}) from GraphQL execution context,
 * local context, or Spring Security Authentication context.
 */
public final class InjectedValueResolver {

    private static final Logger log = LoggerFactory.getLogger(InjectedValueResolver.class);

    private InjectedValueResolver() {}

    /**
     * Resolves the value to be injected into the given field from the environment.
     *
     * @param field the field metadata declaring @injected
     * @param env   the GraphQL data fetching environment
     * @return the resolved object value, or null if not resolvable
     */
    public static Object resolve(FieldMetadata field, DataFetchingEnvironment env) {
        if (field == null || !field.isInjected() || env == null) {
            return null;
        }
        String from = field.injectedFrom();
        if (from == null || from.isBlank()) {
            return null;
        }
        return resolve(from, env);
    }

    /**
     * Resolves a value by its source expression key from the GraphQL environment.
     * Supports resolving from:
     * 1. {@code env.getGraphQlContext()} (e.g. "tenantId", "auth.username", "auth.role", custom keys)
     * 2. Nested auth object in context (Map or Principal/Authentication)
     * 3. LocalContext map
     * 4. Spring Security {@code SecurityContextHolder} (e.g. "auth.role", "auth.username", "auth.name")
     *
     * @param from source key (e.g. "tenantId", "auth.username", "auth.role", "auth.name")
     * @param env  the DataFetchingEnvironment
     * @return the resolved object value, or null
     */
    public static Object resolve(String from, DataFetchingEnvironment env) {
        Objects.requireNonNull(from, "from key must not be null");
        if (env == null) {
            return null;
        }

        GraphQLContext graphQlContext = env.getGraphQlContext();
        if (graphQlContext != null) {
            // 1. Direct match in GraphQLContext
            if (graphQlContext.hasKey(from)) {
                Object val = graphQlContext.get(from);
                if (val != null) {
                    return val;
                }
            }

            // 2. Nested "auth." lookup in context object or map
            if (from.startsWith("auth.")) {
                String subKey = from.substring(5);
                if (graphQlContext.hasKey("auth")) {
                    Object authObj = graphQlContext.get("auth");
                    Object val = extractFromAuthObject(authObj, subKey);
                    if (val != null) {
                        return val;
                    }
                }
                if (graphQlContext.hasKey("authentication")) {
                    Object authObj = graphQlContext.get("authentication");
                    Object val = extractFromAuthObject(authObj, subKey);
                    if (val != null) {
                        return val;
                    }
                }
            }
        }

        // 3. LocalContext match
        Object localContext = env.getLocalContext();
        if (localContext instanceof Map<?, ?> localMap) {
            if (localMap.containsKey(from)) {
                Object val = localMap.get(from);
                if (val != null) {
                    return val;
                }
            }
            if (from.startsWith("auth.") && localMap.containsKey("auth")) {
                Object authObj = localMap.get("auth");
                Object val = extractFromAuthObject(authObj, from.substring(5));
                if (val != null) {
                    return val;
                }
            }
        }

        // 4. Spring Security Context via reflection / SecurityContextHolder
        if (from.startsWith("auth.")) {
            Object secVal = resolveFromSpringSecurity(from.substring(5));
            if (secVal != null) {
                return secVal;
            }
        }

        return null;
    }

    private static Object extractFromAuthObject(Object authObj, String subKey) {
        if (authObj == null) {
            return null;
        }
        if (authObj instanceof Map<?, ?> map) {
            return map.get(subKey);
        }
        if ("username".equalsIgnoreCase(subKey) || "name".equalsIgnoreCase(subKey)) {
            try {
                Method m = authObj.getClass().getMethod("getName");
                return m.invoke(authObj);
            } catch (Exception ignored) {}
            try {
                Method m = authObj.getClass().getMethod("getUsername");
                return m.invoke(authObj);
            } catch (Exception ignored) {}
        } else if ("role".equalsIgnoreCase(subKey)) {
            try {
                Method m = authObj.getClass().getMethod("getAuthorities");
                Object auths = m.invoke(authObj);
                if (auths instanceof Collection<?> coll && !coll.isEmpty()) {
                    Object first = coll.iterator().next();
                    if (first != null) {
                        String authority = extractAuthorityString(first);
                        if (authority != null) {
                            return authority.startsWith("ROLE_") ? authority.substring(5) : authority;
                        }
                    }
                }
            } catch (Exception ignored) {}
        }
        return null;
    }

    private static Object resolveFromSpringSecurity(String subKey) {
        try {
            Class<?> holderClass = Class.forName("org.springframework.security.core.context.SecurityContextHolder");
            Method getContextMethod = holderClass.getMethod("getContext");
            Object context = getContextMethod.invoke(null);
            if (context == null) {
                return null;
            }
            Method getAuthMethod = context.getClass().getMethod("getAuthentication");
            Object auth = getAuthMethod.invoke(context);
            if (auth == null) {
                return null;
            }
            Method isAuthMethod = auth.getClass().getMethod("isAuthenticated");
            Boolean isAuth = (Boolean) isAuthMethod.invoke(auth);
            if (isAuth == null || !isAuth) {
                return null;
            }

            if ("username".equalsIgnoreCase(subKey) || "name".equalsIgnoreCase(subKey)) {
                Method getNameMethod = auth.getClass().getMethod("getName");
                return getNameMethod.invoke(auth);
            } else if ("role".equalsIgnoreCase(subKey)) {
                Method getAuthoritiesMethod = auth.getClass().getMethod("getAuthorities");
                Object authorities = getAuthoritiesMethod.invoke(auth);
                if (authorities instanceof Collection<?> coll && !coll.isEmpty()) {
                    Object first = coll.iterator().next();
                    if (first != null) {
                        String authority = extractAuthorityString(first);
                        if (authority != null) {
                            return authority.startsWith("ROLE_") ? authority.substring(5) : authority;
                        }
                    }
                }
            }
        } catch (ClassNotFoundException e) {
            log.trace("Spring Security not found on classpath for @injected resolution");
        } catch (Exception e) {
            log.debug("Failed to resolve '{}' from Spring Security context: {}", subKey, e.getMessage());
        }
        return null;
    }

    private static String extractAuthorityString(Object authorityObj) {
        try {
            Method m = authorityObj.getClass().getMethod("getAuthority");
            Object res = m.invoke(authorityObj);
            return res != null ? res.toString() : null;
        } catch (Exception e) {
            return authorityObj.toString();
        }
    }
}
