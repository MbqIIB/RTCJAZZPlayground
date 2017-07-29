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
























public class StreamQueryCmd
  extends ObjectQueryCmd
{
  public StreamQueryCmd() {}
  
  public static void queryStream(String queryString, ITeamRepository repo, int maxResult, boolean maxDefined, IScmRichClientRestService scmService, IScmClientConfiguration config, IFilesystemRestClient client)
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
      JSONArray jStream = new JSONArray();
      jStream.clear();
      
      int extraResults = maxResult == Integer.MAX_VALUE ? maxResult : maxResult + 1;
      IWorkspaceHandle[] streams = RepoUtil.findStreamsQuery(query, repo, extraResults, config);
      if ((streams != null) && (streams.length > 0))
      {
        jsonizeStreams(jStream, streams, scmService, repo, config, client, maxResult);
        printStreams(jStream, out, config, (!maxDefined) && (streams.length > maxResult));
      }
    }
    catch (TeamRepositoryException e) {
      String msg = logAndCreateExceptionMessage(queryString, e);
      throw StatusHelper.failure(msg, null); }
    IWorkspaceHandle[] streams;
    if ((streams == null) || (streams.length == 0))
    {
      throw StatusHelper.itemNotFound(NLS.bind(Messages.QueryStreamCmd_NO_ITEM_MATCH, queryString));
    }
  }
  
  private static void printStreams(JSONArray jStreams, IndentingPrintStream out, IScmClientConfiguration config, boolean printMoreMsg) {
    if (config.isJSONEnabled()) {
      config.getContext().stdout().print(jStreams);
      return;
    }
    for (Object obj : jStreams) {
      JSONObject jStream = (JSONObject)obj;
      
      RepoUtil.ItemType itemType = PendingChangesUtil.getWorkspaceType((String)jStream.get("type"));
      out.println(NLS.bind(Messages.ListCmd_Workspace_Header, 
        AliasUtil.selector(config.getAliasConfig(), (String)jStream.get("name"), 
        UUID.valueOf((String)jStream.get("uuid")), 
        (String)jStream.get("url"), itemType), jStream.get("owner")));
    }
    
    if (printMoreMsg) {
      config.getContext().stdout().println(NLS.bind(Messages.ListCmd_MORE_ITEMS_AVAILABLE, CommonOptions.OPT_MAXRESULTS.getName()));
    }
  }
  


  private static void jsonizeStreams(JSONArray jStreams, IWorkspaceHandle[] streams, IScmRichClientRestService scmService, ITeamRepository repo, IScmClientConfiguration config, IFilesystemRestClient client, int maxResult)
    throws FileSystemException
  {
    int loopCount = Math.min(maxResult, streams.length);
    List<IWorkspaceHandle> streamList = new ArrayList(loopCount);
    
    for (int count = 0; count < loopCount; count++) {
      IWorkspaceHandle handle = streams[count];
      streamList.add(handle);
    }
    PendingChangesUtil.PendingChangesOptions options = new PendingChangesUtil.PendingChangesOptions();
    options.enablePrinter(0);
    
    JSONPrintUtil.jsonizeWorkspacesCustomAttributes(jStreams, repo, streamList, options, client, config);
  }
}
