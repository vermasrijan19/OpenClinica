package org.akaza.openclinica.web.restful;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.sql.DataSource;

import net.sf.json.JSON;
import net.sf.json.JSONObject;
import net.sf.json.xml.XMLSerializer;

import org.akaza.openclinica.bean.extract.odm.FullReportBean;
import org.akaza.openclinica.bean.login.UserAccountBean;
import org.akaza.openclinica.bean.managestudy.StudyBean;
import org.akaza.openclinica.bean.managestudy.StudySubjectBean;
import org.akaza.openclinica.dao.managestudy.StudyDAO;
import org.akaza.openclinica.dao.managestudy.StudySubjectDAO;
import org.akaza.openclinica.dao.submit.SubjectDAO;
import org.akaza.openclinica.i18n.core.LocaleResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;

/***
 * * Rest service for ODM clinical data usage
 * ROOT_CONTEXT/rest/clinicaldata/{format}/{mode}/{STUDYOID} format:xml/ json
 * mode:view
 *
 * @author jnyayapathi
 *
 */

@Controller
@RequestMapping("/clinicaldata")
public class ODMClinicaDataResource {
	private static final Logger LOGGER = LoggerFactory
			.getLogger(ODMClinicaDataResource.class);
	private static final int INDENT_LEVEL = 2;


	private ClinicalDataCollectorResource clinicalDataCollectorResource;
	private MetadataCollectorResource metadataCollectorResource;
	private DataSource dataSource;

	public MetadataCollectorResource getMetadataCollectorResource() {
		return metadataCollectorResource;
	}

	public void setMetadataCollectorResource(
			MetadataCollectorResource metadataCollectorResource) {
		this.metadataCollectorResource = metadataCollectorResource;
	}

	public ClinicalDataCollectorResource getClinicalDataCollectorResource() {
		return clinicalDataCollectorResource;
	}

	public void setClinicalDataCollectorResource(
			ClinicalDataCollectorResource clinicalDataCollectorResource) {
		this.clinicalDataCollectorResource = clinicalDataCollectorResource;
	}

	public DataSource getDataSource() {
		return dataSource;
	}

	public void setDataSource(DataSource dataSource) {
		this.dataSource = dataSource;
	}

	private ResponseEntity<String> toStringResponse(String body, String contentType) {
		MediaType mediaType = MediaType.parseMediaType(contentType);
		if (body == null) {
			return ResponseEntity.status(HttpStatus.NO_CONTENT).contentType(mediaType).build();
		}
		return ResponseEntity.ok().contentType(mediaType).body(body);
	}

	/**
	 * @api {get} /rest/clinicaldata/json/view/:study/:subject/:event/:form Retrieve case report forms - JSON
	 * @apiVersion 3.8.0
	 * @apiName getODMClinicaldataJSON
	 * @apiGroup Subject
	 * @apiPermission user
	 *
	 * @apiDescription Annotated case report forms in printable HTML format. Use asterisks in place of OIDs as wildcards
	 *
	 * @apiParam {String} study Study or Site OID.
	 * @apiParam {String} subject Subject Key or ID.
	 * @apiParam {String} event Study Event Definition OID. Use '*' for all.
	 * @apiParam {String} form Case Report Form Version OID. Use '*' for all.
	 *
	 *
	 * @apiError NoAccessRight Only authenticated users can access the data.
	 * @apiError NotFound   The resource was not found.
	 *
	 */
	@RequestMapping(value = "/json/view/{studyOID}/{studySubjectIdentifier}/{studyEventOID}/{formVersionOID}", method = RequestMethod.GET, produces = "application/json;charset=UTF-8")
	public ResponseEntity<String> getODMClinicaldataSpring(
			@PathVariable("studyOID") String studyOID,
			@PathVariable("formVersionOID") String formVersionOID,
			@PathVariable("studyEventOID") String studyEventOID,
			@PathVariable("studySubjectIdentifier") String studySubjectIdentifier,
			@RequestParam(value = "includeDNs", required = false) String includeDns,
			@RequestParam(value = "includeAudits", required = false) String includeAudits,
			HttpServletRequest request) {
		return toStringResponse(
				getODMClinicaldata(
						studyOID,
						formVersionOID,
						studyEventOID,
						studySubjectIdentifier,
						includeDns == null ? "n" : includeDns,
						includeAudits == null ? "n" : includeAudits,
						request),
				"application/json;charset=UTF-8");
	}

	public String getODMClinicaldata(String studyOID,
			String formVersionOID,
			String studyEventOID,
			String studySubjectIdentifier,
			String includeDns,
			String includeAudits, HttpServletRequest request) {
		LOGGER.debug("Requesting clinical data resource");
		boolean includeDN=false;
		boolean includeAudit= false;
		if(includeDns.equalsIgnoreCase("no")||includeDns.equalsIgnoreCase("n")) includeDN=false;
		if(includeAudits.equalsIgnoreCase("no")||includeAudits.equalsIgnoreCase("n")) includeAudit=false;
		if(includeDns.equalsIgnoreCase("yes")||includeDns.equalsIgnoreCase("y")) includeDN=true;
		if(includeAudits.equalsIgnoreCase("yes")||includeAudits.equalsIgnoreCase("y")) includeAudit=true;
		int userId = ((UserAccountBean)request.getSession().getAttribute("userBean")).getId();
		XMLSerializer xmlSerializer = new XMLSerializer();
		FullReportBean report = getMetadataCollectorResource().collectODMMetadataForClinicalData(studyOID,
						formVersionOID,
						getClinicalDataCollectorResource()
								.generateClinicalData(studyOID, getStudySubjectOID(studySubjectIdentifier,studyOID),
										studyEventOID, formVersionOID,includeDN,includeAudit,request.getLocale(), userId));
		if(report.getClinicalDataMap()==null)
		    return null;
		report.createOdmXml(true);
		//xmlSerializer.setForceTopLevelObject(true);
		xmlSerializer.setTypeHintsEnabled(true);
		JSON json = xmlSerializer.read(report.getXmlOutput().toString().trim());

		JSONClinicalDataPostProcessor processor = new JSONClinicalDataPostProcessor(request.getLocale());
        processor.process((JSONObject) json);

//        JSONClinicalDataPostProcessor processor = new JSONClinicalDataPostProcessor(LocaleResolver.getLocale(request));
//        processor.process(json);

		return json.toString(INDENT_LEVEL);
	}

	/**
	 * @api {get} /rest/clinicaldata/html/print/:study/:subject/:event/:form Retrieve case report forms - HTML
	 * @apiVersion 3.8.0
	 * @apiName getPrintCRFController
	 * @apiGroup Subject
	 * @apiPermission user
	 *
	 * @apiDescription Annotated case report forms in printable HTML format. Use asterisks in place of OIDs as wildcards
	 *
	 * @apiParam {String} study Study or Site OID.
	 * @apiParam {String} subject Subject Key or ID.
	 * @apiParam {String} event Study Event Definition OID. Use '*' for all.
	 * @apiParam {String} form Case Report Form Version OID. Use '*' for all.
	 *
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
	@RequestMapping(value = "/html/print/{studyOID}/{studySubjectIdentifier}/{eventOID}/{formVersionOID}", method = RequestMethod.GET)
	public ModelAndView getPrintCRFController(HttpServletRequest request,
			HttpServletResponse response,
			@PathVariable("studyOID") String studyOID,
			@PathVariable("studySubjectIdentifier") String studySubjectIdentifier,
			@PathVariable("eventOID") String eventOID,
			@PathVariable("formVersionOID") String formVersionOID,
			@RequestParam(value = "includeDNs", required = false) String includeDns,
			@RequestParam(value = "includeAudits", required = false) String includeAudits)
			throws Exception {
		request.setCharacterEncoding("UTF-8");
		request.setAttribute("studyOID", studyOID);
		request.setAttribute("studySubjectOID", getStudySubjectOID(studySubjectIdentifier,studyOID));
		request.setAttribute("eventOID", eventOID);
		request.setAttribute("formVersionOID", formVersionOID);
		request.setAttribute("includeAudits", includeAudits == null ? "n" : includeAudits);
		request.setAttribute("includeDNs", includeDns == null ? "n" : includeDns);
		return new ModelAndView("/WEB-INF/jsp/printcrf.jsp");
	}


	/**
	 * @api {get} /rest/clinicaldata/xml/view/:study/:subject/:event/:form Retrieve case report forms - XML
	 * @apiVersion 3.8.0
	 * @apiName getODMMetadata
	 * @apiGroup Subject
	 * @apiPermission user
	 *
	 * @apiDescription Annotated case report forms in printable HTML format. Use asterisks in place of OIDs as wildcards
	 *
	 * @apiParam {String} study Study or Site OID.
	 * @apiParam {String} subject Subject Key or ID.
	 * @apiParam {String} event Study Event Definition OID. Use '*' for all.
	 * @apiParam {String} form Case Report Form Version OID. Use '*' for all.
	 *
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
	@RequestMapping(value = "/xml/view/{studyOID}/{studySubjectIdentifier}/{studyEventOID}/{formVersionOID}", method = RequestMethod.GET, produces = "text/xml;charset=UTF-8")
	public ResponseEntity<String> getODMMetadataSpring(
			@PathVariable("studyOID") String studyOID,
			@PathVariable("formVersionOID") String formVersionOID,
			@PathVariable("studySubjectIdentifier") String studySubjectIdentifier,
			@PathVariable("studyEventOID") String studyEventOID,
			@RequestParam(value = "includeDNs", required = false) String includeDns,
			@RequestParam(value = "includeAudits", required = false) String includeAudits,
			HttpServletRequest request) {
		return toStringResponse(
				getODMMetadata(
						studyOID,
						formVersionOID,
						studySubjectIdentifier,
						studyEventOID,
						includeDns == null ? "n" : includeDns,
						includeAudits == null ? "n" : includeAudits,
						request),
				"text/xml;charset=UTF-8");
	}

	public String getODMMetadata(String studyOID,
			String formVersionOID,
			String studySubjectIdentifier,
			String studyEventOID,
			String includeDns,
			String includeAudits, HttpServletRequest request) {
		LOGGER.debug("Requesting clinical data resource");
		boolean includeDN=false;
		boolean includeAudit= false;
		int userId = ((UserAccountBean)request.getSession().getAttribute("userBean")).getId();

		if(includeDns.equalsIgnoreCase("no")||includeDns.equalsIgnoreCase("n")) includeDN=false;
		if(includeAudits.equalsIgnoreCase("no")||includeAudits.equalsIgnoreCase("n")) includeAudit=false;
		if(includeDns.equalsIgnoreCase("yes")||includeDns.equalsIgnoreCase("y")) includeDN=true;
		if(includeAudits.equalsIgnoreCase("yes")||includeAudits.equalsIgnoreCase("y")) includeAudit=true;
		FullReportBean report = getMetadataCollectorResource()
				.collectODMMetadataForClinicalData(
						studyOID,
						formVersionOID,
						getClinicalDataCollectorResource()
								.generateClinicalData(studyOID, getStudySubjectOID(studySubjectIdentifier,studyOID),
										studyEventOID, formVersionOID,includeDN,includeAudit,request.getLocale(), userId));

		report.createOdmXml(true);
		LOGGER.debug(report.getXmlOutput().toString().trim());

		return report.getXmlOutput().toString().trim();
	}

	/**
	 *  This function checks to see whether the supplied subject identifier is a Study Subject OID or a Study Subject ID.
	 *  If a valid Study Subject OID or wildcard (*) is supplied, just return it.
	 *  If a Study Subject ID is supplied, a lookup is done to get the Study Subject OID and return that.
	 */
	private String getStudySubjectOID(String subjectIdentifier, String studyOID)
	{
		StudySubjectDAO studySubjectDAO = new StudySubjectDAO(getDataSource());
		StudySubjectBean studySubject = studySubjectDAO.findByOid(subjectIdentifier);
		if (subjectIdentifier.equals("*") ||
				(studySubject != null  && studySubject.getOid() != null)) return subjectIdentifier;
		else
		{
			StudyDAO studyDAO = new StudyDAO(getDataSource());
			StudyBean study = studyDAO.findByOid(studyOID);
			studySubject = studySubjectDAO.findByLabelAndStudy(subjectIdentifier,study);
			if (studySubject != null && studySubject.getOid() != null) return studySubject.getOid();
			else return subjectIdentifier;
		}
	}

}
