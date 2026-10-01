// SPDX-License-Identifier: Apache-2.0
// Originally developed by Telicent Ltd.; subsequently adapted, enhanced, and maintained by the National Digital Twin Programme.
/*
 *  Copyright (c) Telicent Ltd.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
/*
 *  Modifications made by the National Digital Twin Programme (NDTP)
 *  © Crown Copyright 2026. This work has been developed by the National Digital Twin Programme
 *  and is legally attributed to the UK's Department for Business, Innovation, Science and Trade (BIST) as the governing entity.
 */

package yamlconfig;

import org.apache.jena.datatypes.xsd.XSDDatatype;
import org.apache.jena.rdf.model.*;
import org.apache.jena.vocabulary.RDF;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


/** Responsible for generating the final RDF config file from ConfigStructs. */
public class RDFConfigGenerator {

    public static final String CXT_NAME = "cxtName";
    public static final String CXT_VALUE = "cxtValue";


    /** Takes a ConfigStruct object and generates an RDF model from it.  */
    public Model createRDFModel(ConfigStruct config) {
        Model model = ModelFactory.createDefaultModel();
        // standard hardcoded prefixes
        populateModelProperties(config, model);

        Resource serverRes = getResource(config, model);

        int i = 1;
        List<Database> databases = config.getDatabases();
        Map<String, Resource> databaseMap = new LinkedHashMap<>();
        for (Database database : databases) {
            Resource databaseRes = model.createResource(ConfigConstants.NS + database.name());

            populateDatabase(database, model, databaseRes);
            databaseMap.put(database.name(), databaseRes);
            i++;
        }

        populateServiceRes(config, model, databaseMap, serverRes);

        i = 1;
        populateConnectorRes(config, model, i);
        return model;
    }

    private void populateDatabase(Database database, Model model, Resource databaseRes) {
        if (ConfigConstants.ABAC.equals(database.dbType())) {
            checkSettingsAndPopulateResource(database.settings(), model, databaseRes);
            checkDatabaseAttributes(database, databaseRes, model);
            checkCacheAndPopulateDB(database, databaseRes, model);
        }
        else {
            if (ConfigConstants.TIM.equals(database.dbType())) {
                databaseRes.addProperty(RDF.type, ResourceFactory.createResource(ConfigConstants.JA_NS + "MemoryDataset"));
            } else if (ConfigConstants.TDB2.equals(database.dbType())) {
                databaseRes.addProperty(RDF.type, ResourceFactory.createResource(ConfigConstants.TDB2_NS + "DatasetTDB2"));
            }

            if (database.data() != null && !database.data().isEmpty())
                databaseRes.addProperty(model.createProperty(ConfigConstants.JA_NS + "data"), database.data());
            if (database.location() != null && !database.location().isEmpty())
                databaseRes.addProperty(model.createProperty(ConfigConstants.TDB2_NS + "location"), database.location());
            checkSettingsAndPopulateResource(database.settings(), model, databaseRes);
        }
    }

    private void populateServiceRes(ConfigStruct config, Model model,
        Map<String, Resource> databaseMap, Resource serverRes) {
        int i;
        RDFList RDFListServices = model.createList();
        List<Service> services = config.getServices();
        i = 1;
        for (Service service : services) {
            Resource serviceRes = model.createResource(ConfigConstants.NS + "service" + i)
                .addProperty(RDF.type, ResourceFactory.createResource(ConfigConstants.FUSEKI_NS + "Service"))
                .addProperty(model.createProperty(ConfigConstants.FUSEKI_NS + "name"), service.name());

            List<Endpoint> endpoints = service.endpoints();
            populateEndpointRes(endpoints, model, serviceRes);
            String dbName = service.database();
            Resource databaseResource = databaseMap.get(service.database());
            if (databaseResource == null) {
                throw new RuntimeException("Database not found: " + dbName);
            }
            serviceRes.addProperty(model.createProperty(ConfigConstants.FUSEKI_NS + "dataset"), databaseResource);
            RDFListServices = RDFListServices.with(serviceRes);
            i++;
        }
        serverRes.addProperty(model.createProperty(ConfigConstants.FUSEKI_NS + "services"), RDFListServices);
    }

    private void checkDatabaseAttributes(Database database, Resource databaseRes, Model model) {
        databaseRes.addProperty(RDF.type, ResourceFactory.createResource(ConfigConstants.AUTHZ_NS + "DatasetAuthz"));

        Resource underlyingDatabaseRes = model.createResource(ConfigConstants.NS + database.dataset());
        databaseRes.addProperty(model.createProperty(ConfigConstants.AUTHZ_NS + "dataset"), underlyingDatabaseRes);
        if(database.attributes() != null && !database.attributes().isEmpty())
            databaseRes.addProperty(model.createProperty(ConfigConstants.AUTHZ_NS + "attributes"), model.createResource("file:" + database.attributes()));
        else
            databaseRes.addProperty(model.createProperty(ConfigConstants.AUTHZ_NS + "attributesURL"), database.attributesURL());

        if(database.hierarchiesURL() != null && !database.hierarchiesURL().isEmpty())
            databaseRes.addProperty(model.createProperty(ConfigConstants.AUTHZ_NS + "hierarchiesURL"), database.hierarchiesURL());
        if(database.labels() != null && !database.labels().isEmpty())
            databaseRes.addProperty(
                model.createProperty(ConfigConstants.AUTHZ_NS + "labels"), model.createResource("file:" + database.labels()));
        if(database.labelsStore() != null && !database.labelsStore().isEmpty()) {
            Resource labelsStoreRes = model.createResource();
            labelsStoreRes.addProperty(model.createProperty(ConfigConstants.AUTHZ_NS + "labelsStorePath"), database.labelsStore());
            databaseRes.addProperty(model.createProperty(ConfigConstants.AUTHZ_NS + "labelsStore"), labelsStoreRes);
        }
        if(database.tripleDefaultLabels() != null && !database.tripleDefaultLabels().isEmpty()) {
            databaseRes.addProperty(model.createProperty(ConfigConstants.AUTHZ_NS + "tripleDefaultLabels"),  database.tripleDefaultLabels());
        }
        databaseRes.addProperty(model.createProperty(ConfigConstants.AUTHZ_NS + "cache"), model.createTypedLiteral(Boolean.parseBoolean(
            database.cache())));
    }

    private void populateEndpointRes(List<Endpoint> endpoints, Model model, Resource serviceRes) {
        if (endpoints != null && !endpoints.isEmpty()) {
            for (Endpoint endpoint : endpoints) {
                Resource endpointRes = model.createResource();
                if (endpoint.name() != null && !endpoint.name().isEmpty())
                    endpointRes.addProperty(model.createProperty(ConfigConstants.FUSEKI_NS + "name"), endpoint.name());
                endpointRes.addProperty(model.createProperty(ConfigConstants.FUSEKI_NS + "operation"),
                    ResourceFactory.createResource(prefixedOrDefault(model, endpoint.operation(), ConfigConstants.FUSEKI_NS)));
                checkSettingsAndPopulateResource(endpoint.settings(), model, endpointRes);
                serviceRes.addProperty(model.createProperty(ConfigConstants.FUSEKI_NS + "endpoint"), endpointRes);
            }
        }
    }

    private void populateConnectorRes(ConfigStruct config, Model model, int i) {
        List<Connector> connectors = config.getConnectors();
        if(connectors != null) {
            for (Connector connector : connectors) {
                Resource connectorRes = model.createResource(ConfigConstants.NS + "connector" + i)
                    .addProperty(RDF.type, ResourceFactory.createResource(ConfigConstants.FK_NS + "Connector"))
                    .addProperty(model.createProperty(ConfigConstants.FK_NS + "fusekiServiceName"), connector.fusekiServiceName())
                    .addProperty(model.createProperty(ConfigConstants.FK_NS + "bootstrapServers"), connector.bootstrapServers())
                    .addProperty(model.createProperty(ConfigConstants.FK_NS + "topic"), connector.topic())
                    .addProperty(model.createProperty(ConfigConstants.FK_NS + "stateFile"), connector.stateFile())
                    .addProperty(model.createProperty(ConfigConstants.FK_NS + "replayTopic"), model.createTypedLiteral(Boolean.parseBoolean(connector.replayTopic())));
                if(!connector.groupId().isEmpty())
                    connectorRes.addProperty(model.createProperty(ConfigConstants.FK_NS + "groupId"), connector.groupId());
                if(!connector.configFile().isEmpty())
                    connectorRes.addProperty(model.createProperty(ConfigConstants.FK_NS + "configFile"), connector.configFile());
                else {
                    Property configProperty = model.createProperty(ConfigConstants.FK_NS + "config");
                    for (Map.Entry<String, String> entry : connector.config().entrySet()) {
                        RDFList configListTemp = model.createList();
                        configListTemp = configListTemp.with(model.createLiteral(entry.getKey()));
                        configListTemp = configListTemp.with(model.createLiteral(String.valueOf(entry.getValue())));
                        connectorRes.addProperty(configProperty, configListTemp);
                    }
                }
                i++;
            }
        }
    }

    private void checkSettingsAndPopulateResource(Map<String, String> settings, Model model, Resource resource) {
        if (settings != null && !settings.isEmpty()) {
            Resource settingsRes = model.createResource();
            for (Map.Entry<String, String> entry : settings.entrySet()) {
                settingsRes.addProperty(model.createProperty(ConfigConstants.JA_NS + CXT_NAME),
                        entry.getKey())
                    .addProperty(model.createProperty(ConfigConstants.JA_NS + CXT_VALUE),
                        String.valueOf(entry.getValue()));
            }
            resource.addProperty(model.createProperty(ConfigConstants.JA_NS + "context"), settingsRes);
        }
    }

    private void checkCacheAndPopulateDB(Database database, Resource databaseRes, Model model) {
        if(database.cache().equals("true")) {
            if(database.attributeCacheSize() != null && !database.attributeCacheSize().isEmpty()) {
                try {
                    databaseRes.addProperty(model.createProperty(ConfigConstants.AUTHZ_NS + "attributeCacheSize"), model.createTypedLiteral(Integer.parseInt(
                        database.attributeCacheSize()), XSDDatatype.XSDinteger));
                }
                catch(NumberFormatException ex) {
                    throw new IllegalArgumentException("The value of \"attributeCacheSize\" is \"" + database.attributeCacheSize() + "\" which is not a valid number.");
                }
            }
            if(database.attributeCacheExpiryTime() != null && !database.attributeCacheExpiryTime().isEmpty())
                databaseRes.addProperty(model.createProperty(ConfigConstants.AUTHZ_NS + "attributeCacheExpiryTime"), model.createTypedLiteral(
                    database.attributeCacheExpiryTime(), XSDDatatype.XSDduration));

            checkHierarchyCacheSizeAndTime(database, databaseRes, model);
        }
    }

    private void checkHierarchyCacheSizeAndTime(Database database, Resource databaseRes,
        Model model) {
        if(database.hierarchyCacheSize() != null && !database.hierarchyCacheSize().isEmpty()) {
            try {
                databaseRes.addProperty(model.createProperty(ConfigConstants.AUTHZ_NS + "hierarchyCacheSize"), model.createTypedLiteral(Integer.parseInt(
                    database.hierarchyCacheSize()), XSDDatatype.XSDinteger));
            }
            catch(NumberFormatException ex) {
                throw new IllegalArgumentException("The value of \"hierarchyCacheSize\" is \"" + database.hierarchyCacheSize() + "\" which is not a valid number.");
            }
        }
        if(database.hierarchyCacheExpiryTime() != null && !database.hierarchyCacheExpiryTime().isEmpty())
            databaseRes.addProperty(model.createProperty(ConfigConstants.AUTHZ_NS + "hierarchyCacheExpiryTime"), model.createTypedLiteral(
                database.hierarchyCacheExpiryTime(), XSDDatatype.XSDduration));
    }

    private Resource getResource(ConfigStruct config, Model model) {
        Server server = config.getServer();
        Resource serverRes = model.createResource()
            .addProperty(RDF.type, ResourceFactory.createResource(ConfigConstants.FUSEKI_NS + "Server"))
            .addProperty(model.createProperty(ConfigConstants.FUSEKI_NS + "name"), server.name());
        if (server.settings() != null && !server.settings().isEmpty()) {
            Resource settingsRes = model.createResource();
            for (Map.Entry<String, String> entry : server.settings().entrySet()) {
                settingsRes.addProperty(model.createProperty(ConfigConstants.JA_NS + CXT_NAME), entry.getKey())
                    .addProperty(model.createProperty(ConfigConstants.JA_NS + CXT_VALUE), String.valueOf(entry.getValue()));
            }
            serverRes.addProperty(model.createProperty(ConfigConstants.JA_NS + "context"), settingsRes);
        }
        return serverRes;
    }

    private void populateModelProperties(ConfigStruct config, Model model) {
        model.setNsPrefix("", ConfigConstants.NS);
        model.setNsPrefix("fuseki", ConfigConstants.FUSEKI_NS);
        model.setNsPrefix("rdf", ConfigConstants.RDF_NS);
        model.setNsPrefix("ja", ConfigConstants.JA_NS);
        model.setNsPrefix("tdb2", ConfigConstants.TDB2_NS);
        model.setNsPrefix("authz", ConfigConstants.AUTHZ_NS);
        model.setNsPrefix("fk", ConfigConstants.FK_NS);
        // user defined prefixes
        if (config.getPrefixes() != null)
            model.setNsPrefixes(config.getPrefixes());
    }

    public String prefixedOrDefault(Model model, String prefixed, String defaultPrefix) {
        if (ConfigConstants.isPrefixed(prefixed))
            return model.expandPrefix(prefixed);
        else
            return defaultPrefix + prefixed;
    }
}
