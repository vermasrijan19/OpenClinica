package org.akaza.openclinica.web.restful;

import java.util.Locale;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.ModelAndView;

import freemarker.template.Configuration;
import freemarker.template.ObjectWrapper;
import freemarker.template.TemplateExceptionHandler;
/**
 *  Rest service for ODM metadata
 *  usage ROOT_CONTEXT/rest/metadata/{format}/{mode}/{STUDYOID}
 *  format:xml/ json
 *  mode:view
 * @author jnyayapathi
 *
 */
@Controller
@RequestMapping("/metadata")
public class ODMMetadataRestResource {

	  private static final Logger LOGGER = LoggerFactory.getLogger(ODMMetadataRestResource.class);

private MetadataCollectorResource metadataCollectorResource;


	public MetadataCollectorResource getMetadataCollectorResource() {
	return metadataCollectorResource;
}


public void setMetadataCollectorResource(
		MetadataCollectorResource metadataCollectorResource) {
	this.metadataCollectorResource = metadataCollectorResource;
}

	private ResponseEntity<String> toStringResponse(String body, String contentType) {
		MediaType mediaType = MediaType.parseMediaType(contentType);
		if (body == null) {
			return ResponseEntity.status(HttpStatus.NO_CONTENT).contentType(mediaType).build();
		}
		return ResponseEntity.ok().contentType(mediaType).body(body);
	}


	/**
	 * @api {get} /rest/metadata/xml/view/:study/:event/:form Retrieve CDISC ODM case report form definitions - XML
	 * @apiVersion 3.8.0
	 * @apiName GetODM
	 * @apiGroup Study
	 * @apiPermission user
	 *
	 * @apiDescription Retrieve CDISC ODM XML study definition. Use asterisks in place of OIDs as wildcards
	 *
	 * @apiParam {String} study Study or Site OID. Use '*' for all.
	 * @apiParam {String} event Study Event Definition OID. Use '*' for all.
	 * @apiParam {String} form Case Report Form Version OID. Use '*' for all.
	 *
	 * @apiExample Example usage - gets all forms in a study:
	 * curl -i demo2.eclinicalhosting.com/OpenClinica9/rest/metadata/xml/view/S_NCT02438/SE_ABC/F_123
	 *
	 * @apiSuccess {String}   ODM            The study metadata in ODM XML.
	 *
	 * @apiSuccessExample {xml} Example success (xml):
	 *     HTTP/1.1 200 Success
	 *     {
	 *       "odm": "study ODM XML metadata here"
	 *     }
	 *
	 * @apiError NoAccessRight Only authenticated users can access the data.
	 * @apiError NotFound   The resource was not found.
	 *
	 * @apiErrorExample Response (example):
	 *     HTTP/1.1 401 Not Authenticated
	 *     {
	 *       "error": "NoAccessRight"
	 *     }
	 */
	@RequestMapping(value = "/xml/view/{studyOID}", method = RequestMethod.GET, produces = "text/xml;charset=UTF-8")
	public ResponseEntity<String> getODMMetadataSpring(@PathVariable("studyOID") String studyOID) {
		return toStringResponse(getODMMetadata(studyOID), "text/xml;charset=UTF-8");
	}

	public String getODMMetadata(String studyOID ){
		LOGGER.debug("returning here........"+studyOID);
		//return "ODM";

		return metadataCollectorResource.collectODMMetadata(studyOID);
	}



	/**
	 * @api {get} /rest/metadata/json/view/:study/:event/:form Retrieve CDISC ODM case report form definitions - JSON
	 * @apiVersion 3.8.0
	 * @apiName GetODMJSON
	 * @apiGroup Study
	 * @apiPermission user
	 *
	 * @apiDescription Retrieve CDISC ODM JSON study definition. Use asterisks in place of OIDs as wildcards
	 *
	 * @apiParam {String} study Study or Site OID. Use '*' for all.
	 * @apiParam {String} event Study Event Definition OID. Use '*' for all.
	 * @apiParam {String} form Case Report Form Version OID. Use '*' for all.
	 *
	 * @apiExample Example usage - gets all forms in a study:
	 * curl -i demo2.eclinicalhosting.com/OpenClinica9/rest/metadata/json/view/S_NCT02438/SE_ABC/F_123
	 *
	 * @apiSuccess {String}   ODM            The form(s) in ODM JSON.
	 *
	 * @apiSuccessExample {json} Example success (json):
	 *     HTTP/1.1 200 Success
	 *     {
	 *       "odm": "study ODM metadata here"
	 *     }
	 *
	 * @apiError NoAccessRight Only authenticated users can access the data.
	 * @apiError NotFound   The resource was not found.
	 *
	 * @apiErrorExample Response (example):
	 *     HTTP/1.1 401 Not Authenticated
	 *     {
	 *       "error": "NoAccessRight"
	 *     }
	 */
	@RequestMapping(value = "/json/view/{studyOID}", method = RequestMethod.GET, produces = "application/json;charset=UTF-8")
	public ResponseEntity<String> getODMMetadataJsonSpring(@PathVariable("studyOID") String studyOID) {
		return toStringResponse(getODMMetadataJson(studyOID), "application/json;charset=UTF-8");
	}

	public String getODMMetadataJson(String studyOID ){
		LOGGER.debug("returning here........"+studyOID);
		//return "ODM";

		return metadataCollectorResource.collectODMMetadataJson(studyOID);
	}


	/**
	 * @api {get} /rest/metadata/:html/print/:study/:event/:form Retrieve printable blank case report forms
	 * @apiVersion 3.8.0
	 * @apiName GetPrintableCRF
	 * @apiGroup Study
	 * @apiPermission user
	 *
	 * @apiDescription Annotated case report forms in printable HTML format. Use asterisks in place of OIDs as wildcards
	 *
	 * @apiParam {String} study Study or Site OID. Use '*' for all.
	 * @apiParam {String} event Study Event Definition OID. Use '*' for all.
	 * @apiParam {String} form Case Report Form Version OID. Use '*' for all.
	 *
	 * @apiExample Example usage - gets all forms in a study:
	 * curl -i demo2.eclinicalhosting.com/OpenClinica9/rest/metadata/html/print/S_NCT02438/SE_ABC/F_123
	 *
	 * @apiSuccess {String}   ODM            The form(s) in HTML.
	 *
	 * @apiSuccessExample {html} Example success (html):
	 *     HTTP/1.1 200 Success
	 *     {
	 *       "odm": "forms displayed here"
	 *     }
	 *
	 * @apiError NoAccessRight Only authenticated users can access the data.
	 * @apiError NotFound   The resource was not found.
	 *
	 * @apiErrorExample Response (example):
	 *     HTTP/1.1 401 Not Authenticated
	 *     {
	 *       "error": "NoAccessRight"
	 *     }
	 */
  @RequestMapping(value = "/html/print/{studyOID}/{eventOID}/{formVersionOID}", method = RequestMethod.GET)
  public ModelAndView getPrintCRFController(
    HttpServletRequest request,
    HttpServletResponse response,
    @PathVariable("studyOID") String studyOID,
    @PathVariable("eventOID") String eventOID,
    @PathVariable("formVersionOID") String formVersionOID
    ) throws Exception {
      request.setAttribute("studyOID", studyOID);
      request.setAttribute("eventOID", eventOID);
      request.setAttribute("formVersionOID", formVersionOID);
      return new ModelAndView("/WEB-INF/jsp/printcrf.jsp");
  }


//  @GET
 // @Path("/pdf/print/{studyOID}/{eventOID}/{formVersionOID}")
  //JN: Commenting out this part of pdf generation written by Nick as the approach might be different. Look for these files in mercurial repo for history.
 /*	public javax.ws.rs.core.Response getPdf(@PathParam("studyOID") String studyOID,@PathParam("formVersionOID") String formVersionOID,
 	    @PathParam("eventOID") String eventOID, @Context HttpServletRequest request, @Context HttpServletResponse response ) {
      JSON json = metadataCollectorResource.collectODMMetadataJson(studyOID,formVersionOID);
      try {
		getPrintServer(request, response, json, studyOID, eventOID, formVersionOID);
	} catch (Exception e) {
		// TODO Auto-generated catch block
		e.printStackTrace();
	}
    return javax.ws.rs.core.Response.ok().type("application/pdf").build();
 }*/

 /*
   @GET
  @Path("/pdf/print/{studyOID}/{eventOID}/{formVersionOID}")
 	public javax.ws.rs.core.Response getPdf2(@PathParam("studyOID") String studyOID,@PathParam("formVersionOID") String formVersionOID,
 	    @Context HttpServletRequest request, @Context HttpServletResponse response ) {
      JSON json = metadataCollectorResource.collectODMMetadataJson(studyOID,formVersionOID);
      try {
		getPrintServer(request, response, json);
	} catch (Exception e) {
		// TODO Auto-generated catch block
		e.printStackTrace();
	}
      File file = new File("...");
      return javax.ws.rs.core.Response.ok(file, MediaType.APPLICATION_OCTET_STREAM)
            .header("content-disposition", "attachment; filename =" + file.getName())
            .build();
 	}
 	*/


	@RequestMapping(value = "/xml/view/{studyOID}/{studyEventDefinitionOId}/{formVersionOID}", method = RequestMethod.GET, produces = "text/xml;charset=UTF-8")
	public ResponseEntity<String> getODMMetadataWithFormVersionOIDSpring(
			@PathVariable("studyOID") String studyOID,
			@PathVariable("formVersionOID") String formVersionOID) {
		return toStringResponse(getODMMetadataWithFormVersionOID(studyOID, formVersionOID), "text/xml;charset=UTF-8");
	}

	public String getODMMetadataWithFormVersionOID(String studyOID, String formVersionOID ){
		LOGGER.debug("returning here........"+formVersionOID);
		return metadataCollectorResource.collectODMMetadataForForm(studyOID,formVersionOID);
	}


    @RequestMapping(value = "/json/view/{studyOID}/{studyEventDefinitionOId}/{formVersionOID}", method = RequestMethod.GET, produces = "application/json;charset=UTF-8")
    public ResponseEntity<String> getODMMetadataJsonSpring(
    		@PathVariable("studyOID") String studyOID,
    		@PathVariable("formVersionOID") String formVersionOID) {
    	return toStringResponse(getODMMetadataJson(studyOID, formVersionOID), "application/json;charset=UTF-8");
    }

 	public String getODMMetadataJson(String studyOID, String formVersionOID ){
 		LOGGER.debug("returning here........"+formVersionOID);
    	return metadataCollectorResource.collectODMMetadataJsonString(studyOID,formVersionOID);
 	}


 /**/

  private Configuration initFreemarker(ServletContext context) {
    // Initialize the FreeMarker configuration;
    // - Create a configuration instance
    Configuration cfg = new freemarker.template.Configuration();
    // - Templates are stoted in the WEB-INF/templates directory of the Web app.
    cfg.setServletContextForTemplateLoading(context, "WEB-INF/template");
    // - Set update dealy to 0 for now, to ease debugging and testing.
    //   Higher value should be used in production environment.
    cfg.setTemplateUpdateDelay(0);
    // - Set an error handler that prints errors so they are readable with
    //   a HTML browser.
    cfg.setTemplateExceptionHandler(TemplateExceptionHandler.HTML_DEBUG_HANDLER);
    // - Use beans wrapper (recommmended for most applications)
    cfg.setObjectWrapper(ObjectWrapper.BEANS_WRAPPER);
    // - Set the default charset of the template files
    cfg.setDefaultEncoding("ISO-8859-1");
    // - Set the charset of the output. This is actually just a hint, that
    //   templates may require for URL encoding and for generating META element
    //   that uses http-equiv="Content-type".
    cfg.setOutputEncoding("UTF-8");
    // - Set the default locale
    cfg.setLocale(Locale.US);

    return cfg;
  }

}
