package org.grails.cxf

import grails.spring.BeanBuilder

import org.codehaus.groovy.grails.commons.GrailsApplication
import org.codehaus.groovy.grails.commons.GrailsClass
import org.grails.cxf.artefact.DefaultGrailsEndpointClass
import org.grails.cxf.artefact.EndpointArtefactHandler
import org.grails.cxf.artefact.EndpointBeanConfiguration

import spock.lang.Specification

class MockConfigurationSpec extends Specification {

    protected class TestOneEndpoint {}
    protected class TestTwoEndpoint {}

    Closure getArtefactsMock = {String type ->
        assert EndpointArtefactHandler.TYPE == type
        return [new DefaultGrailsEndpointClass(TestOneEndpoint),
                new DefaultGrailsEndpointClass(TestTwoEndpoint)] as GrailsClass[]
    }

    Closure getCxfConfig = {
        Class scriptClass = getClass().classLoader.loadClass('DefaultCxfConfig')
        ConfigObject config = new ConfigSlurper().parse(scriptClass)
        println config.toString()
        return config
    }

    Closure getServiceClassesMock = {
        return []
    }

    GrailsApplication grailsApplicationMock = [
            getArtefacts: getArtefactsMock,
            getConfig: getCxfConfig] as GrailsApplication
    EndpointBeanConfiguration bc
    BeanBuilder bb

    def setup() {
        //for some reason have to metaclass this as the mock grails application won't take it as a map
        //probably due to it being a "helper" method that redirects to getArtefacts?!
        grailsApplicationMock.metaClass.getServiceClasses = getServiceClassesMock
        bc = new EndpointBeanConfiguration(grailsApplicationMock)
        bb = new BeanBuilder()
    }
}
