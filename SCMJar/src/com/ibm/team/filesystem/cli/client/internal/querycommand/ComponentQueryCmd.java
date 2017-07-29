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
import com.ibm.team.scm.common.IComponent;
import com.ibm.team.scm.common.IGenericQueryNode;
import com.ibm.team.scm.common.rest.IScmRichClientRestService;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import org.eclipse.osgi.util.NLS;























public class ComponentQueryCmd
  extends ObjectQueryCmd
{
  public ComponentQueryCmd() {}
  
  public static void queryComponent(String queryString, ITeamRepository repo, int maxResult, boolean maxDefined, IScmRichClientRestService scmService, IScmClientConfiguration config, IFilesystemRestClient client)
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
      JSONArray jComponents = new JSONArray();
      jComponents.clear();
      
      int extraResults = maxResult == Integer.MAX_VALUE ? maxResult : maxResult + 1;
      IComponent[] components = RepoUtil.findComponentsQuery(query, repo, extraResults, config);
      if ((components != null) && (components.length > 0))
      {
        jsonizeComponents(jComponents, components, scmService, repo, config, client, maxResult);
        printComponents(jComponents, out, config, (!maxDefined) && (components.length > maxResult));
      }
    }
    catch (TeamRepositoryException e) {
      String msg = logAndCreateExceptionMessage(queryString, e);
      throw StatusHelper.failure(msg, null); }
    IComponent[] components;
    if ((components == null) || (components.length == 0))
    {
      throw StatusHelper.itemNotFound(NLS.bind(Messages.QueryComponentCmd_NO_ITEM_MATCH, queryString));
    }
  }
  
  private static void printComponents(JSONArray jComponents, IndentingPrintStream out, IScmClientConfiguration config, boolean printMoreMsg) {
    if (config.isJSONEnabled()) {
      config.getContext().stdout().print(jComponents);
      return;
    }
    for (Object obj : jComponents) {
      JSONObject jComp = (JSONObject)obj;
      

      String description = (String)jComp.get("description");
      if (description == null) {
        description = "";
      }
      out.println(NLS.bind(Messages.ListCmd_Workspace_Header, 
        AliasUtil.selector(config.getAliasConfig(), (String)jComp.get("name"), 
        UUID.valueOf((String)jComp.get("uuid")), 
        (String)jComp.get("url"), RepoUtil.ItemType.COMPONENT), description));
    }
    if (printMoreMsg) {
      config.getContext().stdout().println(NLS.bind(Messages.ListCmd_MORE_ITEMS_AVAILABLE, CommonOptions.OPT_MAXRESULTS.getName()));
    }
  }
  


  private static void jsonizeComponents(JSONArray jComponents, IComponent[] components, IScmRichClientRestService scmService, ITeamRepository repo, IScmClientConfiguration config, IFilesystemRestClient client, int maxResult)
    throws FileSystemException
  {
    int loopCount = Math.min(maxResult, components.length);
    List<IComponent> componentList = new ArrayList(loopCount);
    
    for (int count = 0; count < loopCount; count++) {
      IComponent handle = components[count];
      componentList.add(handle);
    }
    
    JSONPrintUtil.jsonizeComponentCustomAttributes(jComponents, repo, client, componentList, config);
  }
}
