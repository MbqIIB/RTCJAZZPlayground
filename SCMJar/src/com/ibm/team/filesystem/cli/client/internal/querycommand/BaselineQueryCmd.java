package com.ibm.team.filesystem.cli.client.internal.querycommand;

import com.ibm.team.filesystem.cli.client.internal.Messages;
import com.ibm.team.filesystem.cli.core.subcommands.CommonOptions;
import com.ibm.team.filesystem.cli.core.subcommands.IScmClientConfiguration;
import com.ibm.team.filesystem.cli.core.util.AliasUtil;
import com.ibm.team.filesystem.cli.core.util.JSONPrintUtil;
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
import com.ibm.team.scm.common.IBaseline;
import com.ibm.team.scm.common.IGenericQueryNode;
import com.ibm.team.scm.common.rest.IScmRichClientRestService;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import org.eclipse.osgi.util.NLS;

























public class BaselineQueryCmd
  extends ObjectQueryCmd
{
  public BaselineQueryCmd() {}
  
  public static void queryBaseline(String queryString, ITeamRepository repo, int maxResult, boolean maxDefined, IScmRichClientRestService scmService, IScmClientConfiguration config, IFilesystemRestClient client)
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
      JSONArray jBaselines = new JSONArray();
      jBaselines.clear();
      
      int extraResults = maxResult == Integer.MAX_VALUE ? maxResult : maxResult + 1;
      IBaseline[] baselines = RepoUtil.findBaselinesQuery(query, repo, extraResults, config);
      if ((baselines != null) && (baselines.length > 0))
      {
        jsonizeBaselines(jBaselines, baselines, scmService, repo, config, client, maxResult);
        printBaselines(jBaselines, out, config, (!maxDefined) && (baselines.length > maxResult));
      }
    }
    catch (TeamRepositoryException e) {
      String msg = logAndCreateExceptionMessage(queryString, e);
      throw StatusHelper.failure(msg, null); }
    IBaseline[] baselines;
    if ((baselines == null) || (baselines.length == 0))
    {
      throw StatusHelper.itemNotFound(NLS.bind(Messages.QueryBaselineCmd_NO_ITEM_MATCH, queryString));
    }
  }
  
  private static void printBaselines(JSONArray jBaselines, IndentingPrintStream out, IScmClientConfiguration config, boolean printMoreMsg) {
    if (config.isJSONEnabled()) {
      config.getContext().stdout().print(jBaselines);
      return;
    }
    
    for (Object obj : jBaselines) {
      JSONObject jStream = (JSONObject)obj;
      

      String description = (String)jStream.get("description");
      if (description == null) {
        description = "";
      }
      out.println(NLS.bind(Messages.ListCmd_Workspace_Header, 
        AliasUtil.selector(config.getAliasConfig(), (String)jStream.get("name"), 
        UUID.valueOf((String)jStream.get("uuid")), 
        (String)jStream.get("url"), RepoUtil.ItemType.BASELINE), description));
    }
    if (printMoreMsg) {
      config.getContext().stdout().println(NLS.bind(Messages.ListCmd_MORE_ITEMS_AVAILABLE, CommonOptions.OPT_MAXRESULTS.getName()));
    }
  }
  


  private static void jsonizeBaselines(JSONArray jBaselines, IBaseline[] baselines, IScmRichClientRestService scmService, ITeamRepository repo, IScmClientConfiguration config, IFilesystemRestClient client, int maxResult)
    throws FileSystemException
  {
    int loopCount = Math.min(maxResult, baselines.length);
    List<IBaseline> baselineList = new ArrayList(loopCount);
    
    for (int count = 0; count < loopCount; count++) {
      IBaseline handle = baselines[count];
      baselineList.add(handle);
    }
    
    JSONPrintUtil.jsonizeBaselinesCustomAttributes(jBaselines, repo, client, baselineList, config);
  }
}
