/**
 * Garage Door Sunset Monitor
 *
 * Author: Nic Jansma
 *
 * Licensed under the MIT license
 *
 * Available at: https://github.com/nicjansma/smart-things/
 *
 * Based on:
 *   https://github.com/rogersmj/st-door-alert-after-dark
 *   https://github.com/SmartThingsCommunity/SmartThingsPublic/pull/396/files
 *
 * Starts monitoring at Sunset (or any other specified time), and checks every 5 minutes afterwards to see if
 * there was a state change to notify about.
 *
 * Date: 2016-01-05
 */
definition(
    name: "Garage Door Sunset Monitor",
    namespace: "nicjansma",
    author: "Nic Jansma",
    description: "Sends a notification when doors are open after sunset",
    category: "My Apps",
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/Meta/garage_contact.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Meta/garage_contact@2x.png"
)

//
// Preferences
//
preferences {
    section("Doors to Monitor") {
        input "doors", "capability.contactSensor", title: "Which doors?", multiple: true
    }

    section ("Monitoring Type") {
        paragraph "Do you want to monitor the garage door at specific times of the day, or based on the sunset?"
        input(name: "monitoringType", type: "enum", title: "Monitoring Type", options: ["Time of Day", "Sunset"])
    }

    section ("Time of Day Monitoring") {
        paragraph "If you selected 'Time of Day' monitoring, what times do you want to start/stop monitoring?"
        input "startMonitoring", "time", title: "Start Monitoring (hh:mm 24h)", required: false
        input "stopMonitoring", "time", title: "Stop Monitoring (hh:mm 24h)", required: false
    }

    section ("Sunset Monitoring") {
        paragraph "If you selected 'Sunset' Monitoring, how long before or after sunset do you want the check to happen? (optional)"
        input "sunsetOffsetValue", "text", title: "HH:MM", required: false
        input "sunsetOffsetDir", "enum", title: "Before or After", required: false, metadata: [values: ["Before","After"]]
    }

    section ("Zipcode (optional)") {
        paragraph "Zip code for 'Sunset' Monitoring"
        input "zipCode", "text", title: "Zip Code", required: false
    }

    section ("Alert Thresholds") {
        paragraph "How many minutes should the door be open in the before an alert is fired?  Note doors are only checked every 5 minutes, so you should select a multiple of 5 (0, 5, 10, etc)"
        input "threshold", "number", title: "Minutes (use multiples of 5)", defaultValue: 5, required: true
    }

    section("Notifications") {
        input "sendPushMessage", "enum", title: "Send a push notification?", metadata:[values:["Yes", "No"]], required: false
    }
}

//
// Functions
//
def installed() {
    initialize()
}

def updated() {
    unschedule("sunsetHandler")
    unschedule("sunriseHandler")

    initialize()
}

def initialize() {
    state.monitoring = false
    state.opened = [:]
    state.threshold = 0

    doors?.each { door ->
        state.opened.put(door.displayName, false)
    }

    // only hook into sunrise/sunset if we're doing Sunset monitoring
    if (monitoringType == "Sunset") {
        subscribe(location, "sunsetTime", sunriseSunsetTimeHandler)
        astroCheck()
    }

    runEvery5Minutes(checkDoors)
}

def sunriseSunsetTimeHandler(evt) {
    log.trace "sunriseSunsetTimeHandler()"

    astroCheck()
}

def astroCheck() {
    def s

    if (monitoringType != "Sunset") {
        return
    }

    if (zipCode) {
        s = getSunriseAndSunset(zipCode: zipCode, sunsetOffset: sunsetOffset)
    } else {
        s = getSunriseAndSunset(sunsetOffset: sunsetOffset)
    }

    def now = new Date()
    def sunsetTime = s.sunset
    def sunriseTime = s.sunrise

    log.debug "sunsetTime: $sunsetTime, sunriseTime: $sunriseTime"

    if (state.sunsetTime != sunsetTime.time || state.sunriseTime != sunriseTime.time) {
        unschedule("sunsetHandler")
        unschedule("sunriseHandler")

        // change to the next sunset
        if (sunsetTime.before(now)) {
            log.info "After sunset, starting monitoring"

            state.monitoring = true

            sunsetTime = sunsetTime.next()
        }

        if (sunriseTime.before(now)) {
            log.info "Before sunrise, starting monitoring"

            state.monitoring = true

            sunriseTime = sunriseTime.next()
        }

        state.sunsetTime = sunsetTime.time
        state.sunriseTime = sunriseTime.time

        log.info "Scheduling sunset handler for $sunsetTime, sunrise for $sunriseTime"

        schedule(sunsetTime, sunsetHandler)
        schedule(sunriseTime, sunriseHandler)
    }
}

def sunriseHandler() {
    log.info "Sunrise, stopping monitoring"
    state.monitoring = false
}

def sunsetHandler() {
    log.info "Sunset, starting monitoring"
    state.monitoring = true
}

def checkDoors() {
    // if we're doing Time of Day monitoring, see if this is within the times they specified
    if (monitoringType == "Time of Day") {
        def currTime = now()
        def eveningStartTime = timeToday(startMonitoring)
        def morningEndTime = timeToday(stopMonitoring)

        state.monitoring = currTime >= eveningStartTime.time || currTime <= morningEndTime.time
    }

    log.info "checkDoors: Should we check? $state.monitoring"

    if (!state.monitoring) {
        return
    }

    doors?.each { door ->
        def doorName = door.displayName
        def doorOpen = checkDoor(door)

        log.debug("checkDoors: Door $doorName: $doorOpen, previous state: " + state.opened[doorName])

        if (doorOpen == "open" && !state.opened[doorName]) {
            // previously closed, now open
            state.threshold = state.threshold + 5
            log.debug("checkDoors: Door was closed, is now open.  Threshold check: ${state.threshold} minutes (need " + threshold + "minutes)")

            if (state.threshold >= threshold) {
                log.debug("checkDoors: Door has been open past threshold, sending an alert")

                send("Alert: It's sunset and $doorName is open for $threshold minutes")
                state.opened[doorName] = true
            }
        } else if (doorOpen == "closed" && state.opened[doorName]) {
            // previously open, now closed
            log.debug("checkDoors: Door had been previously open, is now closed")

            send("OK: $doorName closed")

            state.opened[doorName] = false
            state.threshold = 0
        } else if (doorOpen == "closed"){
            // Door closed before threshold, reset threshold
            log.debug("checkDoors: Door closed before " + threshold + " threshold.  Threshold check: ${state.threshold} minutes (threshold reset to 0)")
            state.threshold = 0
        }
    }
}

private send(msg) {
    if (sendPushMessage != "No") {
        sendPush(msg)
    }

    log.debug msg
}

def checkDoor(door) {
    def latestValue = door.currentValue("contact")
}

private getLabel() {
    app.label ?: "SmartThings"
}

private getSunsetOffset() {
    sunsetOffsetValue ? (sunsetOffsetDir == "Before" ? "-$sunsetOffsetValue" : sunsetOffsetValue) : null
}
