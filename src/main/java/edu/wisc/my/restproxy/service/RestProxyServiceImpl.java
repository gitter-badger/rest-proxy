/**
 * 
 */
package edu.wisc.my.restproxy.service;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.util.PropertyPlaceholderHelper;
import org.springframework.web.servlet.HandlerMapping;

import edu.wisc.my.restproxy.KeyUtils;
import edu.wisc.my.restproxy.ProxyRequestContext;
import edu.wisc.my.restproxy.dao.RestProxyDao;

/**
 * Concrete implementation of {@link RestProxyService}.
 *
 * @author Nicholas Blair
 */
@Service
public class RestProxyServiceImpl implements RestProxyService {

  @Autowired
  private Environment env;
  @Autowired
  private RestProxyDao proxyDao;
  private static final Logger logger = LoggerFactory.getLogger(RestProxyServiceImpl.class);

  /**
   * Visible for testing. 
   * 
   * @param env the env to set
   */
  void setEnv(Environment env) {
    this.env = env;
  }
  /**
   * {@inheritDoc}
   * 
   * Inspects the {@link Environment} for necessary properties about the target API:
   * <ul>
   * <li>Resource root URI</li>
   * <li>Credentials</li>
   * </ul>
   * 
   * Delegates to {@link RestProxyDao#proxyRequest(ProxyRequestContext)}
   */
  @Override
  public Object proxyRequest(final String resourceKey, final HttpServletRequest request) {
    final String resourceRoot = env.getProperty(resourceKey + ".uri");
    if(StringUtils.isBlank(resourceRoot)) {
      logger.info("unknown resourceKey {}", resourceKey);
      return null;
    }
    StringBuilder uri = new StringBuilder(resourceRoot);
    
    String resourcePath = (String) request.getAttribute( HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE );
    if(StringUtils.isNotBlank(resourcePath)) {
      if(!StringUtils.endsWith(uri, "/") && !resourcePath.startsWith("/")) {
        uri.append("/");
      }
      uri.append(resourcePath);
    }

    String username = env.getProperty(resourceKey + ".username");
    String password = env.getProperty(resourceKey + ".password");

    ProxyRequestContext context = new ProxyRequestContext(resourceKey)
      .setAttributes(KeyUtils.getHeaders(env, request, resourceKey))
      .setHttpMethod(HttpMethod.valueOf(request.getMethod()))
      .setPassword(password != null ? password.getBytes() : null)
      .setUri(uri.toString())
      .setUsername(username);

    String proxyHeadersValue = env.getProperty(resourceKey + ".proxyHeaders");
    if(proxyHeadersValue != null) {
      String [] proxyHeaders = StringUtils.split(proxyHeadersValue, ",");
      for(String proxyHeader: proxyHeaders) {
        String [] tokens = proxyHeader.split(":");
        if(tokens.length == 2) {
          PropertyPlaceholderHelper helper = new PropertyPlaceholderHelper("{", "}");
          String value = helper.replacePlaceholders(tokens[1], new PropertyPlaceholderHelper.PlaceholderResolver() {
            @Override
            public String resolvePlaceholder(String placeholderName) {
              Object attribute = request.getAttribute(placeholderName);
              if(attribute instanceof String) {
                return (String) attribute;
              }
              logger.warn("configuration error: could not resolve placeholder for attribute {} as it's not a String, it's a {}", placeholderName, attribute.getClass());
              return null;
            }
          });

          context.getHeaders().put(tokens[0], StringUtils.trim(value));
        } else {
          logger.warn("configuration error: can't split {} on ':', ignoring", proxyHeader);
        }
      }
    }
    logger.debug("proxying request {}", context);
    return proxyDao.proxyRequest(context);
  }

}
