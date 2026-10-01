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
import org.yaml.snakeyaml.Yaml;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.*;

import static java.util.Collections.emptyMap;
import static yamlconfig.ConfigConstants.*;


/** Contains all the logic necessary to parse Yaml config files to ConfigStruct objects, called by {@link #runYAMLParser(String)} . */
public class YAMLConfigParser {

    public static final String PREFIX = "prefix";
    public static final String NAMESPACE = "namespace";
    public static final String SERVICE_CONNECTOR = "service connector";
    public static final String FALSE = "false";
    public static final String WHICH_IS_NOT_A_BOOLEAN = "which is not a boolean";

    /** Takes the path to the YAML config file and returns a ConfigStruct object. Throws a RuntimeException.*/
    public ConfigStruct runYAMLParser(String path) throws RuntimeException{
        try {
            Map<String, Object> map = parseYAMLConfigToMap(path);
            ConfigStruct config = mapToConfigStruct(map);
            validateConfigStruct(config);
            return config;
        }
        catch (UncheckedIOException | IllegalArgumentException | ClassCastException | NullPointerException ex) {
            throw new RuntimeException(ex);
        }
    }

    /** Parse a YAML config file to a map using SnakeYaml. */
    public Map<String, Object> parseYAMLConfigToMap(String path) throws UncheckedIOException {
        Yaml yaml = new Yaml();
        try ( InputStream inputStream = new FileInputStream(path) ) {
            return yaml.load(inputStream);
        } catch (IOException e) { throw new UncheckedIOException(e); }
    }

    /** Parse the map created from the YAML config file to a ConfigStruct. */
    public ConfigStruct mapToConfigStruct(Map<String, Object> map) {
        ConfigStruct configStruct = new ConfigStruct();
        if (map.containsKey(VERSION)) {
            configStruct.setVersion(map.get(VERSION).toString());
        } else {
            throw new IllegalArgumentException("'version' field is missing");
        }
        parsePrefixes(map, configStruct);
        parseServer(map, configStruct);
        parseServices(map,configStruct);
        parseDatabases(map, configStruct);
        parseConnectors(map, configStruct);

        return configStruct;
    }

    public void parsePrefixes(Map<String, Object> map, ConfigStruct configStruct) {
        if (map.containsKey(PREFIXES)) {
            Object prefixesObj = map.get(PREFIXES);
            List<Object> prefixesList = castToList("Prefixes", prefixesObj);
            Map<String, String> prefixesMap = new HashMap<>();
            for (Object entry : prefixesList) {
                Map<String, Object> prefixMap = castToMapObject("PrefixPair", entry);
                if(findString(prefixMap, PREFIX, null) == null || findString(prefixMap, PREFIX, null).isEmpty())
                    throw new IllegalArgumentException("The namespace " + findString(prefixMap, NAMESPACE, "") + " has no prefix assigned.");
                if(findString(prefixMap, NAMESPACE, null) == null || findString(prefixMap, NAMESPACE, null).isEmpty())
                    throw new IllegalArgumentException("The prefix " + findString(prefixMap, PREFIX, "") + " has no namespace assigned.");
                if(!prefixesMap.containsKey(findString(prefixMap, PREFIX, "")))
                    prefixesMap.put(findString(prefixMap, PREFIX, ""), findString(prefixMap, NAMESPACE, ""));
                else
                    throw new IllegalArgumentException("The prefix " + findString(prefixMap, PREFIX, "") + " is already assigned to a different uri.");
            }
            configStruct.setPrefixes(prefixesMap);
            configStruct.validatePrefixes();
        }
    }

    public void parseServer(Map<String, Object> map, ConfigStruct configStruct) {
        if (map.containsKey(SERVER)) {
            Object serverObj = map.get(SERVER);
            Map<String, Object> serverMap = castToMapObject("Server", serverObj);
            String name = findString(serverMap, ConfigConstants.NAME, "");
            Map<String, String> settings = findMapString(serverMap, ConfigConstants.SETTINGS, emptyMap());

            // name - required, settings - optional
            if(name == null || name.isEmpty())
                throw new IllegalArgumentException("No name for server");
            Server server = new Server(name, settings);
            configStruct.setServer(server);
        } else {
            throw new IllegalArgumentException("'server' field is missing");
        }
    }

    public void parseEndpoints(Map.Entry<String, Object> field, List<Endpoint> endpoints, String name) {
        List<Object> endpointsList = castToList("Endpoints", field.getValue());
        if (endpointsList == null) {
            log.warn("No endpoints for service {}", name);
        }
        else {
            for (Object endpoint : endpointsList) {
                Map<String, Object> endpointMap = castToMapObject("Endpoint", endpoint);
                String endpointName = findString(endpointMap, ConfigConstants.NAME, "");
                String operation = findString(endpointMap, ConfigConstants.OPERATION, "");
                Map<String, String> settings = findMapString(endpointMap, ConfigConstants.SETTINGS, emptyMap());

                // name - optional, operation - required, settings - optional
                if (operation == null || operation.isEmpty())
                    throw new IllegalArgumentException("No operation defined for endpoint " + endpointName);
                Endpoint tempEndpoint = new Endpoint(endpointName, operation, settings);
                endpoints.add(tempEndpoint);
            }
        }
    }

    public void parseServices(Map<String, Object> map, ConfigStruct configStruct) {
        if (map.containsKey(SERVICES)) {
            Object servicesObj = map.get(SERVICES);
            if (servicesObj == null) {
                log.warn("No services defined");
            }
            else {
                List<Object> servicesList = castToList("Services", servicesObj);
                List<Service> services = new ArrayList<>();
                populateListOfServices(servicesList, services);
                configStruct.setServices(services);
            }
        } else {
            throw new IllegalArgumentException("'services' field is missing");
        }
    }

    private void populateListOfServices(List<Object> servicesList, List<Service> services) {
        for (Object entry : servicesList) {
            Map<String, Object> serviceMap = castToMapObject("Service", entry);
            String name = findString(serviceMap, ConfigConstants.NAME, "");
            List<Endpoint> endpoints = new ArrayList<>();
            for (Map.Entry<String, Object> field : serviceMap.entrySet()) {
                if (field.getKey().equals(ConfigConstants.ENDPOINTS))
                    parseEndpoints(field, endpoints, name);
            }
            String database = findString(serviceMap, ConfigConstants.DATABASE, "");

            // name required, endpoints required, but can be empty, database required
            if (name == null || name.isEmpty())
                throw new IllegalArgumentException("No name defined for service");
            if (database == null || database.isEmpty())
                throw new IllegalArgumentException("No database for service " + name);

            Service serviceTemp = new Service(name, endpoints, database);
            services.add(serviceTemp);
        }
    }

    public void parseDatabases(Map<String, Object> map, ConfigStruct configStruct) {
        if (map.containsKey(DATABASES)) {
            List<Database> databases = new ArrayList<>();
            Object databasesObj = map.get(ConfigConstants.DATABASES);
            List<Object> databasesList = castToList("Databases", databasesObj);
            if (databasesList == null || databasesList.isEmpty())
                throw new NullPointerException("No databases defined");
            for (Object database : databasesList) {
                Map<String, Object> databaseMap = castToMapObject("Database", database);
                String databaseName = findString(databaseMap, NAME, "");
                String dbtype = findString(databaseMap, ConfigConstants.DBTYPE, "");
                String attributes = findString(databaseMap, ConfigConstants.ATTRIBUTES, "");
                String attributesURL = findString(databaseMap, ConfigConstants.ATTRIBUTES_URL, "");
                String labels = findString(databaseMap, ConfigConstants.LABELS, "");
                String labelsStore = findString(databaseMap, ConfigConstants.LABELS_STORE, "");
                String tripleDefaultLabels = findString(databaseMap, ConfigConstants.TRIPLE_DEFAULT_LABELS, "");
                String data = findString(databaseMap, ConfigConstants.DATA, "");
                String location = findString(databaseMap, ConfigConstants.LOCATION, "");
                String dataset = findString(databaseMap, ConfigConstants.DATASET, "");
                String cache = findString(databaseMap, ConfigConstants.CACHE, FALSE);
                String attributeCacheSize = findString(databaseMap, ConfigConstants.ATTRIBUTE_CACHE_SIZE, "");
                String attributeCacheExpiryTime = findString(databaseMap, ConfigConstants.ATTRIBUTE_CACHE_EXPIRY_TIME, "");
                String hierarchiesURL = findString(databaseMap, ConfigConstants.HIERARCHIES_URL, "");
                String hierarchyCacheSize = findString(databaseMap, ConfigConstants.HIERARCHY_CACHE_SIZE, "");
                String hierarchyCacheExpiryTime = findString(databaseMap, ConfigConstants.HIERARCHY_CACHE_EXPIRY_TIME, "");
                Map<String, String> settings = findMapString(databaseMap, ConfigConstants.SETTINGS, emptyMap());

                // name - required, dbtype - required, data - optional, location - required if TDB2, settings - optional

                validateParameters(dbtype, dataset, databaseName, location, attributes, attributesURL, labels,
                    labelsStore, cache,
                    attributeCacheSize, attributeCacheExpiryTime, hierarchyCacheSize,
                    hierarchyCacheExpiryTime);
                Database tempDatabase = new Database(databaseName, dbtype, attributes, attributesURL, labels, labelsStore, tripleDefaultLabels, data, location, dataset, cache, attributeCacheSize, attributeCacheExpiryTime, hierarchiesURL, hierarchyCacheSize, hierarchyCacheExpiryTime, settings);
                databases.add(tempDatabase);
            }
            configStruct.setDatabases(databases);
        } else {
            throw new IllegalArgumentException("'databases' field is missing");
        }
    }

    private void validateParameters(String dbtype, String dataset,
        String databaseName, String location, String attributes,
        String attributesURL, String labels, String labelsStore, String cache,
        String attributeCacheSize, String attributeCacheExpiryTime, String hierarchyCacheSize,
        String hierarchyCacheExpiryTime) {
        if(databaseName == null || databaseName.isEmpty())
            throw new IllegalArgumentException("No database name");
        if(dbtype == null || dbtype.isEmpty())
            throw new IllegalArgumentException("No database type for " + databaseName);
        if((location == null || location.isEmpty()) && dbtype.equals(TDB2))
            throw new IllegalArgumentException("TDB2 database " + databaseName + " is missing location");

        validateAttributesAndCaches(dbtype, dataset, databaseName, attributes, attributesURL, labels, labelsStore,
            cache,
            attributeCacheSize, attributeCacheExpiryTime, hierarchyCacheSize,
            hierarchyCacheExpiryTime);
    }

    private void validateAttributesAndCaches(String dbtype, String dataset, String databaseName, String attributes,
        String attributesURL, String labels, String labelsStore, String cache,
        String attributeCacheSize, String attributeCacheExpiryTime, String hierarchyCacheSize,
        String hierarchyCacheExpiryTime) {
        if(dbtype.equals(ABAC)) {
            vaidateAttributesAndCaches(databaseName, attributes, attributesURL, cache, attributeCacheSize,
                attributeCacheExpiryTime);
            validateLabelsAndHierarchy(dataset, databaseName, labels, labelsStore, hierarchyCacheSize,
                hierarchyCacheExpiryTime);
        }
    }

    private void vaidateAttributesAndCaches(String databaseName, String attributes, String attributesURL,
        String cache, String attributeCacheSize, String attributeCacheExpiryTime) {
        if(attributes == null || attributes.isEmpty()) {
            if (attributesURL == null || attributesURL.isEmpty())
                throw new IllegalArgumentException("ABAC database " + databaseName + " is missing attribute store");
        }
        else if(attributesURL != null && !attributesURL.isEmpty())
                throw new IllegalArgumentException("Both an in-memory and remote attribute store were specified for ABAC database \"" + databaseName
                    + "\". Only one is permitted.");
        if(!cache.equals("true") && !cache.equals(FALSE))
            throw new IllegalArgumentException("The value of \"cache\" on the database \"" + databaseName
                + "\" is \"" + cache +
                "\", " + WHICH_IS_NOT_A_BOOLEAN + ".");
        if(!attributeCacheSize.isEmpty() && !XSDDatatype.XSDduration.isValid(
            attributeCacheExpiryTime))
            throw new IllegalArgumentException("The value of \"attributeCacheExpiryTime\" on the database \"" + databaseName
                + "\" is \"" + attributeCacheExpiryTime + "\", which is not a valid time.");
    }

    private void validateLabelsAndHierarchy(String dataset, String databaseName, String labels, String labelsStore,
        String hierarchyCacheSize, String hierarchyCacheExpiryTime) {
        if(dataset == null || dataset.isEmpty())
            throw new IllegalArgumentException("ABAC database " + databaseName + " is missing dataset");
        if((labels != null && !labels.isEmpty()) && (labelsStore != null && !labelsStore.isEmpty()))
            throw new IllegalArgumentException("Both a labels file and a labels store were specified for ABAC database \"" + databaseName
                + "\". Only one is permitted.");
        if(!hierarchyCacheSize.isEmpty() && !XSDDatatype.XSDduration.isValid(
            hierarchyCacheExpiryTime))
            throw new IllegalArgumentException("The value of \"hierarchyCacheExpiryTime\" is \"" + hierarchyCacheExpiryTime
                + "\" which is not a valid time.");
    }

    public void parseConnectors(Map<String, Object> map, ConfigStruct configStruct) {
        if (map.containsKey(CONNECTORS)) {
            List<Connector> connectors = new ArrayList<>();
            Object connectorsObj = map.get(ConfigConstants.CONNECTORS);
            List<Object> connectorsList = castToList("Connectors", connectorsObj);
            if (connectorsList != null && !connectorsList.isEmpty()) {
                populateConnectors(connectorsList, connectors);
                configStruct.setConnectors(connectors);
            }
        }
    }

    private void populateConnectors(List<Object> connectorsList, List<Connector> connectors) {
        for (Object connector : connectorsList) {
            Map<String, Object> connectorMap = castToMapObject("Connector", connector);
            String fusekiServiceName = findString(connectorMap, FUSEKI_SERVICE, "");
            String topic = findString(connectorMap, ConfigConstants.TOPIC, "");
            String bootstrapServers = findString(connectorMap, ConfigConstants.BOOTSTRAP_SERVERS, "");
            String stateFile = findString(connectorMap, ConfigConstants.STATE_FILE, "");
            String groupId = findString(connectorMap, ConfigConstants.GROUP_ID, "");
            String replayTopic = findString(connectorMap, ConfigConstants.REPLAY_TOPIC, FALSE);
            String syncTopic = findString(connectorMap, ConfigConstants.SYNC_TOPIC, FALSE);
            Map<String, String> config = findMapString(connectorMap, ConfigConstants.CONFIG, emptyMap());
            String configFile = findString(connectorMap, ConfigConstants.CONFIG_FILE, "");

            // fusekiServiceName - required, topic - required, bootstrapServers - required, stateFile - required
            validateFileTopicAndServers(fusekiServiceName, topic, bootstrapServers, stateFile, replayTopic,
                syncTopic);
            Connector tempConnector = new Connector(fusekiServiceName, topic, bootstrapServers, stateFile, groupId, replayTopic, syncTopic, config, configFile);
            connectors.add(tempConnector);
        }
    }

    private void validateFileTopicAndServers(String fusekiServiceName, String topic, String bootstrapServers,
        String stateFile, String replayTopic, String syncTopic) {
        if (fusekiServiceName == null || fusekiServiceName.isEmpty())
            throw new IllegalArgumentException("No destination Fuseki service name.");
        if (topic == null || topic.isEmpty())
            throw new IllegalArgumentException("Missing topic on the \"" + fusekiServiceName +
                "\" " + SERVICE_CONNECTOR + ".");
        if (bootstrapServers == null || bootstrapServers.isEmpty())
            throw new IllegalArgumentException("The \"bootstrapSevers\" field is empty on the \"" + fusekiServiceName
                +
                "\" " + SERVICE_CONNECTOR + ".");
        if (stateFile == null || stateFile.isEmpty())
            throw new IllegalArgumentException("The \"stateFile\" field is empty on the \"" + fusekiServiceName
                +
                "\" " + SERVICE_CONNECTOR + ".");
        if(!replayTopic.equals("true") && !replayTopic.equals(FALSE))
            throw new IllegalArgumentException("The value of \"replayTopic\" on the \"" + fusekiServiceName
                +
                "\" " + SERVICE_CONNECTOR + " is \"" + replayTopic + "\", "
                + WHICH_IS_NOT_A_BOOLEAN + ".");
        if(!syncTopic.equals("true") && !syncTopic.equals(FALSE))
            throw new IllegalArgumentException("The value of \"syncTopic\" on the \"" + fusekiServiceName
                +
                "\" " + SERVICE_CONNECTOR + " is \"" + syncTopic + "\", "
                + WHICH_IS_NOT_A_BOOLEAN + ".");
    }

    public void validateConfigStruct(ConfigStruct configStruct) {
        if (!configStruct.checkDatabaseMismatch())
            throw new IllegalArgumentException("Mismatch between the databases referenced in services and the existing databases.");
        if (!configStruct.checkConnectorMismatch())
            throw new IllegalArgumentException("Mismatch between existing services and the destination services of the connectors.");
        try {
            configStruct.checkEndpointOperations();
            configStruct.checkDatabaseTypes();
            configStruct.checkForLabelsInABACtdb2();
        }
        catch (RuntimeException ex) {
            throw new IllegalArgumentException(ex.getMessage());
        }
    }

    private ArrayList<Object> castToList(String name, Object object) {
        ArrayList<Object> list;
        try {
            list = (ArrayList<Object>) object;
        }
        catch (ClassCastException ex) {
            throw new ClassCastException(name + " cannot be parsed to a List, found " + object.getClass().getCanonicalName());
        }
        return list;
    }


    private LinkedHashMap<String, String> castToMapString(String name, Object object) {
        LinkedHashMap<String, String> map;
        try {
            map = (LinkedHashMap<String, String>) object;
        }
        catch (ClassCastException ex) {
            throw new ClassCastException(name + " cannot be parsed to a Map, found " + object.getClass().getCanonicalName());
        }
        return map;
    }

    private LinkedHashMap<String, Object> castToMapObject(String name, Object object) {
        LinkedHashMap<String, Object> map;
        try {
            map = (LinkedHashMap<String, Object>) object;
        }
        catch (ClassCastException ex) {
            throw new ClassCastException(name + " cannot be parsed to a Map, found " + object.getClass().getCanonicalName());
        }
        return map;
    }

    public String findString(Map<String, Object> map, String property, String defaultValue) {
        if (map.containsKey(property)) {
            Object rawValue = map.get(property);
            return rawValue != null ? rawValue.toString() : defaultValue;
        }
        return defaultValue;
    }

    public Map<String, String> findMapString(Map<String, Object> map, String property, Map<String, String> defaultValue) {
        if (map.containsKey(property)) {
            Map<String, String> rawValue = castToMapString(property, map.get(property));
            return rawValue != null ? rawValue : defaultValue;
        }
        return defaultValue;
    }
}
