/**
 *  Copyright 2015 Mitch Pond
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
 * Original source:
 *  https://github.com/mitchpond/SmartThingsPublic/blob/outlink/devicetypes/mitchpond/quirky-outlink.src/quirky-outlink.groovy
 *
 * Modifications from: Nic Jansma:
 *  Reporting of both power (instantaneous W) and energy (overall kWh)
 *  Removal of 'reset' command
 *  Coding style
 */
 
metadata {
    definition (name: "Quirky Outlink", namespace: "nicjansma", author: "Nic Jansma") {
        capability "Actuator"
        capability "Configuration"
        capability "Refresh"
        capability "Energy Meter"
        capability "Power Meter"
        capability "Sensor"
        capability "Switch"

        attribute "energyDisplay", "string"
        attribute "powerDisplay", "string"

        // 01 0104 0002 00 07 0000 0003 0006 0005 0004 FC20 0702 01 0019
        fingerprint endpointId: "01", profileId: "0104", inClusters: "0000, 0003, 0006, 0005, 0004, FC20, 0702", outClusters: "0019", manufacturer: "Quirky", model: "ZHA Smart Plug"
    }

    tiles(scale: 2) {
        multiAttributeTile(name: "switch", type: "lighting", width: 6, height: 4, canChangeIcon: true) {
            tileAttribute("device.switch", key: "PRIMARY_CONTROL") {
                attributeState "on", label:'${name}', action:"switch.off", icon:"st.switches.switch.on", backgroundColor:"#79b821", nextState:"turningOff"
                attributeState "off", label:'${name}', action:"switch.on", icon:"st.switches.switch.off", backgroundColor:"#ffffff", nextState:"turningOn"
                attributeState "turningOn", label:'${name}', action:"switch.off", icon:"st.switches.switch.on", backgroundColor:"#79b821", nextState:"turningOff"
                attributeState "turningOff", label:'${name}', action:"switch.on", icon:"st.switches.switch.off", backgroundColor:"#ffffff", nextState:"turningOn"
            }
            tileAttribute("powerDisplay", key: "SECONDARY_CONTROL") {
                attributeState "default", label:'Power: ${currentValue}', unit: "W"
            }
        }

        valueTile("energyDisplay", "device.energyDisplay", width: 4, height: 1, decoration: "flat") {
            state "default", label:'Energy used: ${currentValue}', unit: "kWh"
        }

        standardTile("refresh", "device.switch", inactiveLabel: false, decoration: "flat", width: 2, height: 2) {
            state "default", label:"", action:"refresh.refresh", icon:"st.secondary.refresh"
        }

        valueTile("powerDisplay", "device.powerDisplay", decoration: "flat", width: 4, height: 1) {
            state "default", label: 'Power: ${currentValue}', unit: "W"
        }

        main "switch"

        details(["switch", "energyDisplay", "refresh", "powerDisplay"])
    }
}

// Parse incoming device messages to generate events
def parse(String description) {
    log.debug "parse: $description"

    def resultMap = zigbee.getKnownDescription(description)

    if (resultMap) {
        if (resultMap.type == "update") {
            log.info "$device updates: ${resultMap.value}"
        } else {
            createEvent(name: resultMap.type, value: resultMap.value)
        }
    } else {
        def descMap = zigbee.parseDescriptionAsMap(description)

        log.debug descMap

        if (descMap.cluster == "0702" && descMap.attrId == "0000") {
            log.debug "Parsing kWh..."
            parseZSEMeteringReport(descMap)
        }
    }
}

def parseZSEMeteringReport(descMap) {
    def energyUsed = decodeHexEnergyUsage(descMap.value)
    def powerUsed = getInstantDemand()

    log.debug "parseZSEMeteringReport: energy: $energyUsed KWh power: $powerUsed W"

    def energyEvent = [createEvent(name: "energy", value: energyUsed)]
    def powerEvent = [createEvent(name: "power", value: powerUsed)]

    def results = 
        energyEvent +
        powerEvent +
        createEvent(name: "energyDisplay",
                    value: String.format("%6.3f kWh", energyUsed),
                    isStateChange: true,
                    displayed: false) +
        createEvent(name: "powerDisplay",
                    value: String.format("%4.2f W", powerUsed),
                    isStateChange: true,
                    displayed: false)

    return results
}

def off() {
    zigbee.off()
}

def on() {
    zigbee.on()
}

def refresh() {
    zigbee.onOffRefresh()

    // read energyJus
    zigbee.readAttribute(0x0702, 0x0000)
}

def configure() {
    log.debug "Configuring Reporting and Bindings."

    zigbee.onOffConfig()

    zigbee.configureReporting(0x0702, 0, 0x25, 30, 60, 0x01)

    refresh()
}

private decodeHexEnergyUsage(String hexValue) {
    // The Outlink reports in kWh, with a conversion factor of 1/3600000
    def rawValue = Long.parseLong(hexValue, 16)
    def scaledValue = rawValue / 3600000

    // round to two digits
    scaledValue = Math.round(scaledValue * 100) / 100

    log.debug "decodeHexEnergyUsage: $rawValue / 3600000 = $scaledValue"

    return scaledValue
}

private getInstantDemand() {
    try {
        // Since the device does not support the reporting of instantaneous demand, we need to derive this
        def recentEvents = device.statesSince("energy", new Date() - 1).collect {[value: it.value as float, date: it.date]}

        log.debug "getInstantDemand: Events: " + recentEvents[0].value + " - " + recentEvents[1].value

        if (Double.isInfinite(recentEvents[0].value) || Double.isInfinite(recentEvents[1].value)) {
            return 0;
        }

        // W*h
        def deltaE = (recentEvents[0].value - recentEvents[1].value) * 1000

        // h
        def deltaT = (recentEvents[0].date.getTime() - recentEvents[1].date.getTime()) / 3600000

        def result = Math.round(deltaE / deltaT * 100) / 100

        if (result < 0) {
            // sanity check for first few measurements
            result = 0.0
        }

        log.debug "getInstantDemand: deltaE: $deltaE deltaT: $deltaT"
        log.debug "getInstantDemand: result: $result"

        return result
    } catch (Exception e) {
        log.debug recentEvents
    }
}