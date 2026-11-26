package org.akaza.openclinica.web.filter.rest;

import java.util.ArrayList;
import java.util.ResourceBundle;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.sql.DataSource;

import org.akaza.openclinica.bean.core.Role;
import org.akaza.openclinica.bean.login.StudyUserRoleBean;
import org.akaza.openclinica.bean.login.UserAccountBean;
import org.akaza.openclinica.bean.managestudy.StudyBean;
import org.akaza.openclinica.control.SpringServletAccess;
import org.akaza.openclinica.control.core.SecureController;
import org.akaza.openclinica.dao.login.UserAccountDAO;
import org.akaza.openclinica.dao.managestudy.StudyDAO;
import org.springframework.web.servlet.HandlerInterceptor;

public class RestODMFilter implements HandlerInterceptor {

	String studyOIDS;

	public static ResourceBundle restext;

	private static String GlOBAL_STUDY_OID = "*";

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
		UserAccountBean userBean = (UserAccountBean) request.getSession().getAttribute("userBean");

		String pathInfo = request.getPathInfo();
		String[] segments = (pathInfo.startsWith("/") ? pathInfo.substring(1) : pathInfo).split("/");
		String studyOID = segments[3];

		//parse to get studyOID

		if (studyOID.equals(GlOBAL_STUDY_OID)) {
			if (checkAuth(userBean)) {
				return true;
			}
		} else {
			DataSource ds = getDataSource(request);
			StudyBean studyBean = getStudyByOID(studyOID, ds);
			if (checkAuth(studyBean, userBean, ds)) {
				return true;
			} else {
				if (studyBean.getParentStudyId() != 0) {
					int parentStudyID = studyBean.getParentStudyId();
					studyBean = getStudyByID(parentStudyID, ds);
					if (checkAuth(studyBean, userBean, ds)) {
						return true;
					}
				}
			}

			request.setAttribute(SecureController.PAGE_MESSAGE, "You don't have correct permission in your current Study.");
		}

		response.sendError(HttpServletResponse.SC_FORBIDDEN);
		return false;
	}

	private Boolean checkAuth(UserAccountBean userBean) {
		Boolean auth = false;

		ArrayList userRoles = userBean.getRoles();
		for (int i = 0; (i < userRoles.size() && auth == false); i++) {
			StudyUserRoleBean studyRole = (StudyUserRoleBean) userRoles.get(i);

			if (studyRole.getRole().equals(Role.ADMIN) || studyRole.getRole().equals(Role.COORDINATOR) || studyRole.getRole().equals(Role.STUDYDIRECTOR)) {
				auth = true;
			}
		}
		return auth;
	}

	private Boolean checkAuth(StudyBean studyBean, UserAccountBean userBean, DataSource ds) {
		Boolean auth = false;
		StudyUserRoleBean studyRole = getRoleByStudy(studyBean, ds, userBean);
		Role r = studyRole.getRole();
		if (r != null) {
			if (r != null && (r.equals(Role.COORDINATOR) || r.equals(Role.STUDYDIRECTOR))) {
				auth = true;
			}
			if (r != null && (r.equals(Role.ADMIN) || r.equals(Role.COORDINATOR) || r.equals(Role.STUDYDIRECTOR) || r.equals(Role.INVESTIGATOR)
					|| r.equals(Role.MONITOR) || r.equals(Role.RESEARCHASSISTANT) || r.equals(Role.RESEARCHASSISTANT2))) {
				auth = true;
			}
		}
		return auth;
	}

	private DataSource getDataSource(HttpServletRequest request) {
		return (DataSource) SpringServletAccess.getApplicationContext(request.getSession().getServletContext()).getBean("dataSource");
	}

	private StudyBean getStudyByOID(String OID, DataSource ds) {
		StudyDAO studyDAO = new StudyDAO(ds);
		return studyDAO.findByOid(OID);
	}

	private StudyUserRoleBean getRoleByStudy(StudyBean studyBean, DataSource ds, UserAccountBean userBean) {
		UserAccountDAO userAccountDAO = new UserAccountDAO(ds);
		return userAccountDAO.findRoleByUserNameAndStudyId(userBean.getName(), studyBean.getId());
	}

	private StudyBean getStudyByID(int id, DataSource ds) {
		StudyDAO studyDAO = new StudyDAO(ds);
		return (StudyBean) studyDAO.findByPK(id);
	}
}
