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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class ConfigConstants {
    public final static Logger log = LoggerFactory.getLogger("fuseki-yaml-config");
    public static final Pattern prefixRegex = Pattern.compile("\\p{Alpha}([\\w.-]*\\w)?");
    public static final Pattern prefixedField = Pattern.compile("^([^:]+):([^:]+)$");

    // database types
    public final static String TIM = "TIM";
    public final  static String TDB2 = "TDB2";
    public final  static String ABAC = "ABAC";

    // URIs
    public static final String NS =  "#";
    public static final String FUSEKI_NS = "http://jena.apache.org/fuseki#";
    public static final String RDF_NS = "http://www.w3.org/1999/02/22-rdf-syntax-ns#";
    public static final String JA_NS = "http://jena.hpl.hp.com/2005/11/Assembler#";
    public static final String TDB2_NS = "http://jena.apache.org/2016/tdb#";
    public static final String AUTHZ_NS = "http://ndtp.co.uk/security#";
    public static final String FK_NS = "http://jena.apache.org/fuseki/kafka#";

    // keys for parsing Yaml configs
    public static final String VERSION = "version";
    public static final String NAME = "name";
    public static final String SETTINGS = "settings";
    public static final String OPERATION = "operation";
    public static final String PREFIXES = "prefixes";
    public static final String SERVER = "server";
    public static final String SERVICES = "services";
    public static final String ENDPOINTS = "endpoints";
    public static final String DATABASE = "database";
    public static final String DATABASES = "databases";
    public static final String CONNECTORS = "connectors";
    public static final String CONNECTOR = "connector";

    // keys for parsing databases
    public static final String DBTYPE = "dbtype";
    public static final String DATA = "data";
    public static final String LOCATION = "location";
    public static final String DATASET = "dataset";
    public static final String ATTRIBUTES = "attributes";
    public static final String ATTRIBUTES_URL = "attributes-url";
    public static final String LABELS = "labels";
    public static final String LABELS_STORE = "labels-store";
    public static final String TRIPLE_DEFAULT_LABELS = "triple-default-labels";
    public static final String CACHE = "cache";
    public static final String ATTRIBUTE_CACHE_SIZE = "attribute-cache-size";
    public static final String ATTRIBUTE_CACHE_EXPIRY_TIME = "attribute-cache-expiry-time";
    public static final String HIERARCHY_CACHE_SIZE = "hierarchy-cache-size";
    public static final String HIERARCHY_CACHE_EXPIRY_TIME = "hierarchy-cache-expiry-time";
    public static final String HIERARCHIES_URL = "hierarchies-url";

    // keys for parsing connectors
    public static final String FUSEKI_SERVICE = "fuseki-service";
    public static final String BOOTSTRAP_SERVERS = "bootstrap-servers";
    public static final String TOPIC = "topic";
    public static final String STATE_FILE = "state-file";
    public static final String GROUP_ID = "group-id";
    public static final String REPLAY_TOPIC = "replay-topic";
    public static final String SYNC_TOPIC = "sync-topic";
    public static final String CONFIG = "config";
    public static final String CONFIG_FILE = "config-file";

    public static Boolean isPrefixed(String field) {
        Matcher matcher = prefixedField.matcher(field);
        return matcher.matches();
    }
}
