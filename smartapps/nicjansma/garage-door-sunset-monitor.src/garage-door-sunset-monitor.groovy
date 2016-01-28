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
 * Starts monitoring at Sunset, and checks every 5 minutes afterwards to see if
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

    section ("Sunset Offset (optional)") {
        paragraph "How long before or after sunset do you want the check to happen?"
        input "sunsetOffsetValue", "text", title: "HH:MM", required: false
        input "sunsetOffsetDir", "enum", title: "Before or After", required: false, metadata: [values: ["Before","After"]]
    }

    section ("Zipcode (optional)") {
        paragraph "Zip code to determine Sunset from."
        input "zipCode", "text", title: "Zip Code", required: false
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
    subscribe(location, "sunsetTime", sunriseSunsetTimeHandler)

    state.monitoring = false
    state.opened = [:]

    doors?.each { door ->
        state.opened.put(door.displayName, false)
    }
    
    astroCheck()

    runEvery5Minutes(checkDoors)
}

def sunriseSunsetTimeHandler(evt) {
    log.trace "sunriseSunsetTimeHandler()"

    astroCheck()
}

def astroCheck() {
    def s

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
    log.info "Checking doors? $state.monitoring"
    if (!state.monitoring) {
        return
    }

    doors?.each { door ->
        def doorName = door.displayName
        def doorOpen = checkDoor(door)

        log.debug("Door $doorName: $doorOpen")

        if (doorOpen == "open" && !state.opened[doorName]) {
            send("Alert: It's sunset and $doorName is open")
            state.opened[doorName] = true
        } else if (doorOpen == "closed" && state.opened[doorName]) {
            send("Alert: $doorName closed")
            state.opened[doorName] = false
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