/**
 *  LG Smart TV Device Type
 *
 *  Copyright 2015 Daniel Vorster
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
metadata {
	definition (name: "LG Smart TV", namespace: "dpvorster", author: "Daniel Vorster") 
    {
        capability "Switch"
        attribute "sessionId", "string"
	}
    
    preferences {
        input("televisionIp", "string", title:"Television IP Address", description: "Television's IP address", required: true, displayDuringSetup: false)
        input("pairingKey", "string", title:"Pairing Key", description: "Pairing key", required: true, displayDuringSetup: false)
	}

	simulator 
    {
		// TODO: define status and reply messages here
	}

	tiles 
    {
        standardTile("power", "device.switch", width: 2, height: 2) {
            state "off", label: "off", icon: "st.switches.switch.off", backgroundColor: "#ffffff", action: "switch.on"
            state "on", label: "on", icon: "st.switches.switch.on", backgroundColor: "#00a0dc", action: "switch.off"
        }
       
	}
}

// parse events into attributes
def parse(String description) 
{
	log.debug "Parsing '${description}'"
    
    if (description == "updated") 
    {
    	sendEvent(name:'refresh', displayed:false)
    }
    else
    {
    	parseHttpResult(description)
    }
}

def refresh() 
{
    log.debug "Executing 'refresh'"
    return sessionIdCommand()
}

def on()
{
	log.debug "on"
    return sendCommand(1)
}

def off() 
{
	log.debug "off"   
    return sendCommand(1)
}

def sendCommand(cmd)
{
	def actions = []
    actions << sessionIdCommand()
    actions << delayHubAction(500)
    actions << tvCommand(cmd)
    actions = actions.flatten()
    return actions
    
}

def sessionIdCommand()
{
    def commandText = "<?xml version=\"1.0\" encoding=\"utf-8\"?><auth><type>AuthReq</type><value>$pairingKey</value></auth>"       
    def httpRequest = [
      	method:		"POST",
        path: 		"/roap/api/auth",
        body:		"$commandText",
        headers:	[
        				HOST:			"$televisionIp:8080",
                        "Content-Type":	"application/atom+xml",
                    ]
	]
    
    try 
    {
    	def hubAction = new physicalgraph.device.HubAction(httpRequest)
        log.debug "hub action: $hubAction"
        return hubAction
    }
    catch (Exception e) 
    {
		log.debug "Hit Exception $e on $hubAction"
	}
}

def tvCommand(cmd)
{
	def sessionId = ""
    def commandText = "<?xml version=\"1.0\" encoding=\"utf-8\"?><command><session>${device.currentValue('sessionId')}</session><type>HandleKeyInput</type><value>${cmd}</value></command>"
    def httpRequest = [
      	method:		"POST",
        path: 		"/roap/api/command",
        body:		"$commandText",
        headers:	[
        				HOST:			"$televisionIp:8080",
                        "Content-Type":	"application/atom+xml",
                    ]
	]
    
    try 
    {
    	def hubAction = new physicalgraph.device.HubAction(httpRequest)
        log.debug "hub action: $hubAction"
    	return hubAction
    }
    catch (Exception e) 
    {
		log.debug "Hit Exception $e on $hubAction"
	}
}

private parseHttpResult (output)
{
	def headers = ""
	def parsedHeaders = ""
    
    def msg = parseLanMessage(output)

    def headersAsString = msg.header // => headers as a string
    def headerMap = msg.headers      // => headers as a Map
    def body = msg.body              // => request body as a string
    def status = msg.status          // => http status code of the response
    def json = msg.json              // => any JSON included in response body, as a data structure of lists and maps
    def xml = msg.xml                // => any XML included in response body, as a document tree structure
    def data = msg.data              // => either JSON or XML in response body (whichever is specified by content-type header in response)

	log.debug "headers: $headerMap, status: $status, body: $body, data: $json"
  
    if (status == 200){
    	parseSessionId(body)
    }
    else if (status == 401){
    	log.debug "Unauthorized - clearing session value"
    	sendEvent(name:'sessionId', value:'', displayed:false)
        sendEvent(name:'refresh', displayed:false)
    }
}

def String parseSessionId(bodyString)
{
	def sessionId = ""
	def body = new XmlSlurper().parseText(bodyString)
  	sessionId = body.session.text()

	if (sessionId != null && sessionId != "")
  	{
  		sendEvent(name:'sessionId', value:sessionId, displayed:false)
  		log.debug "session id: $sessionId"
    }
}

private parseHttpHeaders(String headers) 
{
	def lines = headers.readLines()
	def status = lines[0].split()

	def result = [
	  protocol: status[0],
	  status: status[1].toInteger(),
	  reason: status[2]
	]

	if (result.status == 200) {
		log.debug "Authentication successful! : $status"
	}

	return result
}

private def delayHubAction(ms) 
{
    log.debug("delayHubAction(${ms})")
    return new physicalgraph.device.HubAction("delay ${ms}")
}