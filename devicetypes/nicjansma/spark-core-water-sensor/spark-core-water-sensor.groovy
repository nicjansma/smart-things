/**
 * Spark Core Water, Temperature and Humidity Sensor
 *
 * Author: Nic Jansma
 *
 * Licensed under the MIT license
 *
 * Available at: https://github.com/nicjansma/smart-things/
 *
 * Based on:
 *   https://github.com/krvarma/SmartThings_SparkCore_Sensor/blob/master/sparksensor.device.groovy
 *   https://github.com/nicholaswilde/smartthings/blob/master/device-types/spark-core-temperature-sensor/spark-core-temperature-sensor.device.groovy
 *
 * Device type for a Particle (Spark) Core (or Photon) with a water (and optional temperature / humidity) sensors as described by:
 *   https://github.com/nicjansma/spark-core-water-sensor
 */

preferences {
    input name: "deviceId", type: "text", title: "Device ID", required: true
    input name: "token", type: "password", title: "Access Token", required: true
    input name: "sparkWaterVar", type: "text", title: "Spark Water Variable", required: true, defaultValue: "water"
    input name: "sparkTemperatureVar", type: "text", title: "Spark Temperature Variable", required: true, defaultValue: "temperature"
    input name: "sparkHumidityVar", type: "text", title: "Spark Humidity Variable", required: true, defaultValue: "humidity"
}

metadata {
    definition (name: "Spark Core Water Sensor", namespace: "nicjansma", author: "Nic Jansma") {
        capability "Polling"
        capability "Sensor"
        capability "Refresh"
        capability "Water Sensor"
        capability "Relative Humidity Measurement"
        capability "Temperature Measurement"

        command "wet"
        command "dry"

        attribute "temperature", "number"
    }

    simulator {
        status "dry": "water:dry"
        status "wet": "water:wet"
    }

    tiles(scale: 2) {
        standardTile("water", "device.water", width: 2, height: 2) {
            state "dry", icon:"st.alarm.water.dry", backgroundColor:"#79B821"
            state "wet", icon:"st.alarm.water.wet", backgroundColor:"#53a7c0"
        }

        standardTile("refresh", "device.refresh", inactiveLabel: false, decoration: "flat", width: 2, height: 2) {
            state "default", action:"refresh.refresh", icon:"st.secondary.refresh"
        }

        valueTile("temperature", "device.temperature", width: 2, height: 2) {
            state("temperature", label:'${currentValue}°', unit:"F",
                backgroundColors:[    
                    [value: 31, color: "#153591"],
                    [value: 44, color: "#1e9cbb"],
                    [value: 59, color: "#90d2a7"],
                    [value: 74, color: "#44b621"],
                    [value: 84, color: "#f1d801"],
                    [value: 95, color: "#d04e00"],
                    [value: 96, color: "#bc2323"]
                ]
            )
        }

        valueTile("humidity", "device.humidity", width: 2, height: 2) {
            state "default", label:'${currentValue}%'
        }

        main "water"
        details(["water", "temperature", "humidity", "refresh"])
    }
}

// handle commands
def poll() {
    log.debug "Executing 'poll'"

    getAll()
}

def refresh() {
    log.debug "Executing 'refresh'"

    getAll()
}

def getAll() {
    getWater()
    getTemperature()
    getHumidity()
}

def parse(String description) {
    def pair = description.split(":")

    createEvent(name: pair[0].trim(), value: pair[1].trim())
}

private getWater() {
    def closure = { response ->
        log.debug "Water request was successful, $response.data"

        if (response.data.result) {
            wet();
        } else {
            dry();
        }
    }

    httpGet("https://api.spark.io/v1/devices/${deviceId}/${sparkWaterVar}?access_token=${token}", closure)
}

def wet() {
    log.trace "wet()"

    sendEvent(name: "water", value: "wet")
}

def dry() {
    log.trace "dry()"

    sendEvent(name: "water", value: "dry")
}

private getTemperature() {
    def closure = { response ->
        log.debug "Temperature request was successful, $response.data"

        sendEvent(name: "temperature", value: response.data.result)
    }

    httpGet("https://api.spark.io/v1/devices/${deviceId}/${sparkTemperatureVar}?access_token=${token}", closure)
}

private getHumidity() {
    def closure = { response ->
        log.debug "Humidity request was successful, $response.data"

        sendEvent(name: "humidity", value: response.data.result)
    }

    httpGet("https://api.spark.io/v1/devices/${deviceId}/${sparkHumidityVar}?access_token=${token}", closure)
}
