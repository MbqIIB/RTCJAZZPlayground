package getRN;

import java.io.File;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Map;

/*******************************************************************************
 * Licensed Materials - Property of IBM
 * (c) Copyright IBM Corporation 2006, 2012. All Rights Reserved. 
 * 
 * Note to U.S. Government Users Restricted Rights:  Use, 
 * duplication or disclosure restricted by GSA ADP Schedule 
 * Contract with IBM Corp.
 *******************************************************************************/
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.osgi.util.NLS;

import com.ibm.team.build.internal.PasswordHelper;
import com.ibm.team.filesystem.cli.client.internal.Messages;
import com.ibm.team.filesystem.cli.core.util.RepoUtil;
import com.ibm.team.filesystem.cli.core.util.StatusHelper;
import com.ibm.team.filesystem.client.FileSystemException;
import com.ibm.team.filesystem.common.internal.rest.IFilesystemRestService;
import com.ibm.team.filesystem.common.internal.rest.client.core.PathDTO;
import com.ibm.team.filesystem.common.internal.rest.client.core.ShareDTO;
import com.ibm.team.filesystem.common.internal.rest.client.core.ShareableDTO;
import com.ibm.team.filesystem.common.internal.rest.client.resource.FilePropertiesDTO;
import com.ibm.team.filesystem.common.internal.rest.client.resource.IgnoreReasonDTO;
import com.ibm.team.filesystem.common.internal.rest.client.resource.PermissionsContextDTO;
import com.ibm.team.filesystem.common.internal.rest.client.resource.ResourcePropertiesDTO;
import com.ibm.team.filesystem.common.internal.rest.client.resource.SymlinkPropertiesDTO;
import com.ibm.team.repository.client.ITeamRepository;
import com.ibm.team.repository.client.TeamPlatform;
import com.ibm.team.repository.client.util.IClientLibraryContext;
import com.ibm.team.repository.common.TeamRepositoryException;
import com.ibm.team.rtc.cli.infrastructure.internal.util.IndentingPrintStream;
import com.ibm.team.rtc.cli.infrastructure.internal.util.StringUtil;
import com.ibm.team.scm.common.VersionedContentDeleted;

/**
 * Plain Java Snippet: Connecting to a repository
 */
public class Snippet1 {

	private static String REPOSITORY_ADDRESS = System.getProperty("repositoryAddress",
			"https://jazz-server.elm.com.sa/ccm/");
	private static String USER_AND_PASSWORD = System.getProperty("snippetUserAndPassword", "ccm_user");

	public static void main(String[] args) {
		TeamPlatform.startup();
		try {
			IProgressMonitor monitor = new SysoutProgressMonitor();
			login(monitor);

		} catch (TeamRepositoryException e) {
			System.out.println("Unable to login: " + e.getMessage());
		} finally {
			TeamPlatform.shutdown();
		}
	}

	protected static String decryptPassword(File passwordFile) throws GeneralSecurityException, IOException {
		/* 681 */ return PasswordHelper.getPassword(passwordFile);
		/*      */ }

	public static ITeamRepository login(IProgressMonitor monitor) throws TeamRepositoryException {
		ITeamRepository repository = TeamPlatform.getTeamRepositoryService().getTeamRepository(REPOSITORY_ADDRESS);
		repository.registerLoginHandler(

				new ITeamRepository.ILoginHandler() {
					public ILoginInfo challenge(ITeamRepository repository) {
						return new ILoginInfo() {
							private String address = "C:\\Users\\mbarqawi\\workspace\\getRN\\ccm_user_file.xml";

							public String getUserId() {
								return USER_AND_PASSWORD;
							}

							public String getPassword() {
								File fPasswordFile = new File(address);
								try {
									String blanPassword = decryptPassword(fPasswordFile);
									System.out.print(blanPassword);
									return blanPassword;
								} catch (GeneralSecurityException e) {
									// TODO Auto-generated catch block
									e.printStackTrace();
								} catch (IOException e) {
									// TODO Auto-generated catch block
									e.printStackTrace();
								} // I want to put the password file
								return "";
							}
						};
					}
				});
		monitor.subTask("Contacting " + repository.getRepositoryURI() + "...");
		repository.login(monitor);

		getChangeSetIds(repository);
		monitor.subTask("Connected");
		return repository;
	}

	private static String[] getChangeSetIds(ITeamRepository repo) throws FileSystemException {

		IFilesystemRestService service = (IFilesystemRestService) ((IClientLibraryContext) repo)
				.getServiceInterface(IFilesystemRestService.class);

		IFilesystemRestService.ParmsGetBlame parms = new IFilesystemRestService.ParmsGetBlame();
		parms.fileItemId = "_Q5jvUNDQEea8Js8tQ9Rn4g";
		parms.workspaceItemId = "_py3HUK-3Eea-kot7zHg5jg";
		parms.componentItemId = "_bitsQbFIEea-kot7zHg5jg";
		// https://jazz-server.elm.com.sa/ccm/web/projects/Sectors#
		// action=com.ibm.team.scm.browseElement&workspaceItemId=_py3HUK-3Eea-kot7zHg5jg&componentItemId=_bitsQbFIEea-kot7zHg5jg&itemType=com.ibm.team.filesystem.FileItem&itemId=_TWuUMNJjEeaNMODSAfVH8A
		String[] result=new String[12];
		try

		{

			result = service.getBlame(parms);
			ICommandLine subargs = config.getSubcommandCommandLine();
			 ChangeSetSyncDTO[] csDTOList = RepoUtil.findChangeSets(csSelectors, false, null, null, repo.getRepositoryURI(), 
				      client, config);
			System.out.println(result.length);
			System.out.println(java.util.Arrays.toString(result));
		} catch (TeamRepositoryException e) {
			System.out.println(e.getMessage());

		}

		return result;
	}
	

}
