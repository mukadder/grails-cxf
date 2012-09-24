package org.grails.cxf.artefact;

import groovy.lang.MetaProperty;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.codehaus.groovy.grails.commons.AbstractInjectableGrailsClass;
import org.codehaus.groovy.grails.commons.GrailsClassUtils;
import org.grails.cxf.utils.EndpointType;
import org.grails.cxf.utils.GrailsCxfUtils;

import javax.xml.transform.TransformerConfigurationException;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class DefaultGrailsEndpointClass extends AbstractInjectableGrailsClass implements GrailsEndpointClass {

    private static final String CXFJAX = "cxfjax";
    private static final String CXF = "cxf";
    protected EndpointExposureType expose;
    protected Set<String> excludes;
    protected String servletName;
    protected URL wsdl;
    protected Boolean soap12;

    private static final Log log = LogFactory.getLog(DefaultGrailsEndpointClass.class);

    public DefaultGrailsEndpointClass(Class clazz) throws TransformerConfigurationException {
        super(clazz, EndpointArtefactHandler.TYPE);
        setupExpose();
        buildExclusionSet();
        setupServletName();
        findWsdl();
        setupSoap12Binding();
    }

    /**
     * This can be configured in the endpoint class by setting {@code static expose = 'CXF'}. Valid strings in the
     * endpoint class are {@code CXF}, {@code JAXWS}, and {@code JAXRS}. If the {@code expose} property of the
     * class is not specified, then the default of {@code CXF} will be used.
     *
     * @return @inheritDoc
     */
    public EndpointExposureType getExpose() {
        return expose;
    }

    /**
     * Setting the property {@code static excludes = ['myPublicMethod']} on the endpoint class will allow clients to
     * add additional method names to be excluded from exposure.
     * <p>
     * By default all of the GroovyObject methods and getters and setters for properties will be excluded and setting
     * the excludes property on the endpoint class will add to this set.
     * <p>
     * TODO: I think this is only relevant to SIMPLE Cxf Frontends.
     *
     * @return @inheritDoc
     */
    public Set<String> getExcludes() {
        return excludes;
    }

    /**
     * Since the plugin allows us to configure and use multiple CXF servlets, this property allows us to choose which
     * servlet to use. The servlet name can be configured by using the property servletName on the endpoint class.
     * <p>
     * By default the first alphabetically will be used.
     *
     * @return @inheritDoc
     */
    public String getServletName() {
        return servletName;
    }

    /**
     * Gets the address that will be set on the Cxf ServerFactoryBean.
     * <p>
     * TODO Should also allow overriding and basic configuration?
     *
     * @return @inheritDoc
     */
    public String getAddress() {
        return "/" + getNameNoPostfix();
    }

    /**
     * The URL of the Wsdl that is on the classpath.
     *
     * @return @inheritDoc
     */
    public URL getWsdl() {
        return wsdl;
    }

    public Boolean hasWsdl() {
        return expose == EndpointExposureType.JAX_WS_WSDL && wsdl != null;
    }

    public String getNameNoPostfix() {
        return StringUtils.removeEnd(getPropertyName(), EndpointArtefactHandler.TYPE);
    }

    public Boolean isSoap12() {
        return soap12;
    }

    protected void setupExpose() {
        expose = EndpointExposureType.JAX_WS; // Default to the most common type.

        Object propExpose = getPropertyValue(PROP_EXPOSE);
        String manualExpose = getConfiguredExpose(propExpose);

        if (manualExpose != null && !manualExpose.equals("")) {
            try {
                expose = EndpointExposureType.forExpose(manualExpose);
            } catch (IllegalArgumentException e) {
                log.error("Unsupported endpoint exposure type [" + manualExpose + "] for endpoint [" + getFullName() + "]. Using default type.");
            }
        }

        if(expose.equals(EndpointExposureType.SIMPLE)) {
            log.warn("Simple Cxf Frontends are generally not recommended. Find out more: http://cxf.apache.org/docs/simple-frontend.html");
        }

        log.debug("Endpoint [" + getFullName() + "] configured to use [" + expose.name() + "].");
    }

    private String getConfiguredExpose(Object propExpose) {
        String manualExpose = null;
        if(propExpose instanceof EndpointType){
            manualExpose = ((EndpointType)propExpose).toString();
        } else if(propExpose instanceof String){
            manualExpose = (String)propExpose;
        } else if(propExpose instanceof List){
            manualExpose = getListExpose((List) propExpose);
        }
        return manualExpose;
    }

    /**
     * Support legacy static expose = [] style exposure.
     *
     * @param propExpose list of values to check
     * @return string of the service type to wire
     */
    private String getListExpose(List propExpose) {
        String manualExpose = null;
        for(Object prop : ((List) propExpose)) {
            if(prop instanceof String || prop instanceof EndpointType) {
                String stringProp = prop.toString().toLowerCase();

                //legacy variables cxf and cxfjax
                if(stringProp.equals(CXF)) {
                    return EndpointType.SIMPLE.toString();
                } else if(stringProp.equals(CXFJAX)) {
                    return EndpointType.JAX_WS.toString();
                }

                try {
                    EndpointExposureType type = EndpointExposureType.forExpose(stringProp);
                    manualExpose = type.toString();
                    break;
                } catch(IllegalArgumentException e) {
                    log.debug("could not identify type for " + prop);
                }
            }
        }
        return manualExpose;
    }

    protected void buildExclusionSet() {
        final Set<String> groovyExcludes = DEFAULT_GROOVY_EXCLUDES;

        List<String> automaticExcludes = new ArrayList<String>();
        for (MetaProperty prop : getMetaClass().getProperties()) {
            int modifiers = prop.getModifiers();
            if (Modifier.isPublic(modifiers) && !Modifier.isStatic(modifiers)) {
                automaticExcludes.add(GrailsClassUtils.getGetterName(prop.getName()));
                automaticExcludes.add(GrailsClassUtils.getSetterName(prop.getName()));
            }
        }

        // Get the the methods that are specified for manual exclusion.
        Collection<String> manualExcludes = (Collection<String>) getPropertyOrStaticPropertyOrFieldValue(PROP_EXCLUDES, Collection.class);

        Set<String> aggExcludes = new HashSet<String>();
        aggExcludes.addAll(groovyExcludes);
        aggExcludes.addAll(automaticExcludes);
        if (manualExcludes != null && !manualExcludes.isEmpty()) {
            manualExcludes.remove("");
            aggExcludes.addAll(manualExcludes);
        }

        excludes = Collections.unmodifiableSet(aggExcludes);

        log.debug("Endpoint [" + getFullName() + "] configured to exclude methods " + excludes + ".");
    }

    protected void setupServletName() {
        String manualServletName = (String) getPropertyOrStaticPropertyOrFieldValue(PROP_SERVLET_NAME, String.class);

        if (manualServletName != null && !manualServletName.equals("") && GrailsCxfUtils.getServletsMappings().containsKey(manualServletName)) {
            servletName = manualServletName;
        } else {
            servletName = GrailsCxfUtils.getDefaultServletName();
        }

        log.debug("Endpoint [" + getFullName() + "] configured to servlet [" + servletName + "].");
    }

    protected void findWsdl() {
        String wsdlLocation = (String) getPropertyOrStaticPropertyOrFieldValue(PROP_WSDL, String.class);
        if (wsdlLocation != null && !wsdlLocation.equals("")) {
            wsdl = getClass().getClassLoader().getResource(wsdlLocation);
            if(wsdl == null) {
                log.error("Endpoint [" + getFullName() + "] as WSDL at [" + wsdlLocation + "] but it couldn't be found. Will try to generate the Cxf Service from the endpoint class.");
            }
        }

        log.debug("Endpoint [" + getFullName() + "] configured to use WSDL [" + wsdl + "].");
    }

    protected void setupSoap12Binding() {
        soap12 = GrailsCxfUtils.getDefaultSoap12Binding();

        Boolean soap12setting = (Boolean) getPropertyOrStaticPropertyOrFieldValue(PROP_SOAP12, Boolean.class);
        if(soap12setting != null) {
            soap12 = soap12setting;
        }
    }
}
