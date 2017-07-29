package com.ibm.team.filesystem.cli.client.internal.querycommand;

import com.ibm.team.filesystem.cli.client.internal.Messages;
import com.ibm.team.filesystem.cli.core.subcommands.CommonOptions;
import com.ibm.team.filesystem.cli.core.subcommands.IScmClientConfiguration;
import com.ibm.team.filesystem.cli.core.util.AliasUtil;
import com.ibm.team.filesystem.cli.core.util.JSONPrintUtil;
import com.ibm.team.filesystem.cli.core.util.PendingChangesUtil;
import com.ibm.team.filesystem.cli.core.util.PendingChangesUtil.PendingChangesOptions;
import com.ibm.team.filesystem.cli.core.util.RepoUtil;
import com.ibm.team.filesystem.cli.core.util.RepoUtil.ItemType;
import com.ibm.team.filesystem.cli.core.util.StatusHelper;
import com.ibm.team.filesystem.client.FileSystemException;
import com.ibm.team.filesystem.client.rest.IFilesystemRestClient;
import com.ibm.team.repository.client.ITeamRepository;
import com.ibm.team.repository.common.TeamRepositoryException;
import com.ibm.team.repository.common.UUID;
import com.ibm.team.repository.common.json.JSONArray;
import com.ibm.team.repository.common.json.JSONObject;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IExecutionContext;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.NamedOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.util.IndentingPrintStream;
import com.ibm.team.scm.common.IGenericQueryNode;
import com.ibm.team.scm.common.IWorkspaceHandle;
import com.ibm.team.scm.common.rest.IScmRichClientRestService;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import org.eclipse.osgi.util.NLS;
































public class WorkspaceQueryCmd
  extends ObjectQueryCmd
{
  public WorkspaceQueryCmd() {}
  
  public static void queryWorkspace(String queryString, ITeamRepository repo, int maxResult, boolean maxDefined, IScmRichClientRestService scmService, IScmClientConfiguration config, IFilesystemRestClient client)
    throws FileSystemException
  {
    IndentingPrintStream out = new IndentingPrintStream(config.getContext().stdout());
    
    IGenericQueryNode query = null;
    try
    {
      query = getQuery(queryString, repo);
    } catch (Exception e) {
      String msg = logAndCreateExceptionMessage(queryString, e);
      throw StatusHelper.failure(msg, null);
    }
    try
    {
      JSONArray jWorkspace = new JSONArray();
      jWorkspace.clear();
      
      int extraResults = maxResult == Integer.MAX_VALUE ? maxResult : maxResult + 1;
      IWorkspaceHandle[] workspaces = RepoUtil.findWorkspacesQuery(query, repo, extraResults, config);
      if ((workspaces != null) && (workspaces.length > 0))
      {
        jsonizeWorkspaces(jWorkspace, workspaces, scmService, repo, config, client, maxResult);
        printWorkspaces(jWorkspace, out, config, (!maxDefined) && (workspaces.length > maxResult));
      }
    }
    catch (TeamRepositoryException e) {
      String msg = logAndCreateExceptionMessage(queryString, e);
      throw StatusHelper.failure(msg, null); }
    IWorkspaceHandle[] workspaces;
    if ((workspaces == null) || (workspaces.length == 0))
    {
      throw StatusHelper.itemNotFound(NLS.bind(Messages.QueryWorkspaceCmd_NO_ITEM_MATCH, queryString));
    }
  }
  
  private static void printWorkspaces(JSONArray jWorkspaces, IndentingPrintStream out, IScmClientConfiguration config, boolean printMoreMsg) {
    if (config.isJSONEnabled()) {
      config.getContext().stdout().print(jWorkspaces);
      return;
    }
    for (Object obj : jWorkspaces) {
      JSONObject jWorkspace = (JSONObject)obj;
      
      RepoUtil.ItemType itemType = PendingChangesUtil.getWorkspaceType((String)jWorkspace.get("type"));
      out.println(NLS.bind(Messages.ListCmd_Workspace_Header, 
        AliasUtil.selector(config.getAliasConfig(), (String)jWorkspace.get("name"), 
        UUID.valueOf((String)jWorkspace.get("uuid")), 
        (String)jWorkspace.get("url"), itemType), jWorkspace.get("owner")));
    }
    
    if (printMoreMsg) {
      config.getContext().stdout().println(NLS.bind(Messages.ListCmd_MORE_ITEMS_AVAILABLE, CommonOptions.OPT_MAXRESULTS.getName()));
    }
  }
  

  private static void jsonizeWorkspaces(JSONArray jWorkspaces, IWorkspaceHandle[] workspaces, IScmRichClientRestService scmService, ITeamRepository repo, IScmClientConfiguration config, IFilesystemRestClient client, int maxResult)
    throws FileSystemException
  {
    int loopCount = Math.min(maxResult, workspaces.length);
    List<IWorkspaceHandle> workspaceList = new ArrayList(loopCount);
    
    for (int count = 0; count < loopCount; count++) {
      IWorkspaceHandle handle = workspaces[count];
      workspaceList.add(handle);
    }
    PendingChangesUtil.PendingChangesOptions options = new PendingChangesUtil.PendingChangesOptions();
    options.enablePrinter(0);
    
    JSONPrintUtil.jsonizeWorkspacesCustomAttributes(jWorkspaces, repo, workspaceList, options, client, config);
  }
}
