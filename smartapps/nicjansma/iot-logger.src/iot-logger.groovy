/**
 *  Event Logger
 *
 *  Copyright 2016 Nic Jansma
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 */
/**
 * Original source via Brian Keifer:
 *  https://raw.githubusercontent.com/bkeifer/smartthings/master/Logstash%20Event%20Logger/LogstashEventLogger.groovy
 *
 * Modifications from: Nic Jansma:
 *  https://github.com/nicjansma/smart-things
 *
 * Changes:
 *  Logs to a HTTP endpoint instead of Logstash
 *  Adds `x-api-key` to the HTTP Request Headers
 */
definition(
    name: "IoT Logger",
    namespace: "nicjansma",
    author: "Nic Jansma",
    description: "Log SmartThings events to any HTTP endpoint",
    category: "Convenience",
    iconUrl: "http://cdn.device-icons.smartthings.com/Home/home1-icn.png",
    iconX2Url: "http://cdn.device-icons.smartthings.com/Home/home1-icn@2x.png",
    iconX3Url: "http://cdn.device-icons.smartthings.com/Home/home1-icn@3x.png")

preferences {
    section("Log these presence sensors:") {
        input "presences", "capability.presenceSensor", multiple: true, required: false
    }
    section("Log these switches:") {
        input "switches", "capability.switch", multiple: true, required: false
    }
    section("Log these switch levels:") {
        input "levels", "capability.switchLevel", multiple: true, required: false
    }
    section("Log these motion sensors:") {
        input "motions", "capability.motionSensor", multiple: true, required: false
    }
    section("Log these temperature sensors:") {
        input "temperatures", "capability.temperatureMeasurement", multiple: true, required: false
    }
    section("Log these humidity sensors:") {
        input "humidities", "capability.relativeHumidityMeasurement", multiple: true, required: false
    }
    section("Log these contact sensors:") {
        input "contacts", "capability.contactSensor", multiple: true, required: false
    }
    section("Log these alarms:") {
        input "alarms", "capability.alarm", multiple: true, required: false
    }
    section("Log these indicators:") {
        input "indicators", "capability.indicator", multiple: true, required: false
    }
    section("Log these CO detectors:") {
        input "codetectors", "capability.carbonMonoxideDetector", multiple: true, required: false
    }
    section("Log these smoke detectors:") {
        input "smokedetectors", "capability.smokeDetector", multiple: true, required: false
    }
    section("Log these water detectors:") {
        input "waterdetectors", "capability.waterSensor", multiple: true, required: false
    }
    section("Log these acceleration sensors:") {
        input "accelerations", "capability.accelerationSensor", multiple: true, required: false
    }
    section("Log these power meters:") {
        input "powermeters", "capability.powerMeter", multiple: true, required: false
    }
    section("Log these energy meters:") {
        input "energymeters", "capability.energyMeter", multiple: true, required: false
    }

    section ("HTTP Server") {
        input "httpUrl", "text", title: "HTTP URL"
        input "xApiKey", "text", title: "'x-api-key' header"
    }
}

def installed() {
    log.debug "Installed with settings: ${settings}"

    initialize()
}

def updated() {
    log.debug "Updated with settings: ${settings}"

    unsubscribe()
    initialize()
}

def initialize() {
    doSubscriptions()
}

def doSubscriptions() {
    subscribe(alarms, "alarm", alarmHandler)
    subscribe(codetectors, "carbonMonoxideDetector", coHandler)
    subscribe(contacts, "contact", contactHandler)
    subscribe(indicators, "indicator", indicatorHandler)
    subscribe(modes, "locationMode", modeHandler)
    subscribe(motions, "motion", motionHandler)
    subscribe(presences, "presence", presenceHandler)
    subscribe(relays, "relaySwitch", relayHandler)
    subscribe(smokedetectors, "smokeDetector", smokeHandler)
    subscribe(switches, "switch", switchHandler)
    subscribe(levels, "level", levelHandler)
    subscribe(temperatures, "temperature", temperatureHandler)
    subscribe(waterdetectors, "water", waterHandler)
    subscribe(location, "location", locationHandler)
    subscribe(accelerations, "acceleration", accelerationHandler)
    subscribe(powermeters, "power", powerHandler)
    subscribe(energymeters, "energy", energyHandler)
}

def genericHandler(evt) {
/*
    log.debug("------------------------------")
    log.debug("date: ${evt.date}")
    log.debug("name: ${evt.name}")
    log.debug("displayName: ${evt.displayName}")
    log.debug("device: ${evt.device}")
    log.debug("deviceId: ${evt.deviceId}")
    log.debug("value: ${evt.value}")
    log.debug("isStateChange: ${evt.isStateChange()}")
    log.debug("id: ${evt.id}")
    log.debug("description: ${evt.description}")
    log.debug("descriptionText: ${evt.descriptionText}")
    log.debug("installedSmartAppId: ${evt.installedSmartAppId}")
    log.debug("isoDate: ${evt.isoDate}")
    log.debug("isDigital: ${evt.isDigital()}")
    log.debug("isPhysical: ${evt.isPhysical()}")
    log.debug("location: ${evt.location}")
    log.debug("locationId: ${evt.locationId}")
    log.debug("source: ${evt.source}")
    log.debug("unit: ${evt.unit}")
*/
    def json = "{"
    json += "\"device\":\"${evt.device}\","
    json += "\"deviceId\":\"${evt.deviceId}\","
    json += "\"${evt.name}\":\"${evt.value}\","
    json += "\"location\":\"${evt.location}\","
    json += "\"event\":\"smartthings\""
    json += "}"
    log.debug("JSON: ${json}")

    def params = [
        uri: httpUrl,
        headers: [
            "x-api-key": xApiKey,
            "content-type": "application/json"
        ],
        body: json
    ]

    try {
        httpPostJson(params)
    } catch (groovyx.net.http.HttpResponseException ex) {
        if (ex.statusCode != 200) {
            log.debug "Unexpected response error: ${ex.statusCode}"
            log.debug ex
            log.debug ex.response.contentType
        }
    }
}

def alarmHandler(evt) {
    genericHandler(evt)
}

def coHandler(evt) {
    genericHandler(evt)
}

def indicatorHandler(evt) {
    genericHandler(evt)
}

def presenceHandler(evt) {
    genericHandler(evt)
}

def switchHandler(evt) {
    genericHandler(evt)
}

def smokeHandler(evt) {
    genericHandler(evt)
}

def levelHandler(evt) {
    genericHandler(evt)
}

def contactHandler(evt) {
    genericHandler(evt)
}

def temperatureHandler(evt) {
    genericHandler(evt)
}

def motionHandler(evt) {
    genericHandler(evt)
}

def modeHandler(evt) {
    genericHandler(evt)
}

def relayHandler(evt) {
    genericHandler(evt)
}

def waterHandler(evt) {
    genericHandler(evt)
}

def locationHandler(evt) {
    genericHandler(evt)
}

def accelerationHandler(evt) {
    genericHandler(evt)
}

def powerHandler(evt) {
    genericHandler(evt)
}

def energyHandler(evt) {
    genericHandler(evt)
}