package com.ibm.team.filesystem.cli.client.internal.querycommand;

import com.ibm.team.filesystem.cli.client.internal.Messages;
import com.ibm.team.filesystem.cli.core.subcommands.IScmClientConfiguration;
import com.ibm.team.filesystem.cli.core.util.AliasUtil.IAliasOptions;
import com.ibm.team.filesystem.cli.core.util.JSONPrintUtil;
import com.ibm.team.filesystem.cli.core.util.RepoUtil;
import com.ibm.team.filesystem.cli.core.util.StatusHelper;
import com.ibm.team.filesystem.client.FileSystemException;
import com.ibm.team.repository.client.ITeamRepository;
import com.ibm.team.repository.common.IItemType;
import com.ibm.team.repository.common.TeamRepositoryException;
import com.ibm.team.repository.common.UUID;
import com.ibm.team.repository.common.json.JSONArray;
import com.ibm.team.repository.common.json.JSONObject;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IExecutionContext;
import com.ibm.team.rtc.cli.infrastructure.internal.util.IndentingPrintStream;
import com.ibm.team.scm.common.IFolder;
import com.ibm.team.scm.common.IGenericQueryNode;
import com.ibm.team.scm.common.IVersionable;
import com.ibm.team.scm.common.internal.rich.rest.dto.ScmVersionableIdentifier;
import com.ibm.team.scm.common.internal.rich.rest.dto.ScmVersionableIdentifierList;
import com.ibm.team.scm.common.rest.IScmRichClientRestService;
import java.io.PrintStream;
import java.util.Collections;
import java.util.List;
import org.eclipse.osgi.util.NLS;
















public class VersionableQueryCmd
  extends ObjectQueryCmd
{
  public VersionableQueryCmd() {}
  
  public static void queryVersionables(String queryString, ITeamRepository repo, IScmRichClientRestService scmService, QueryCmd.VersionMode vMode, IScmClientConfiguration config)
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
    try {
      JSONArray jVersionables = new JSONArray();
      
      IVersionable[] versionables = RepoUtil.findVersionablesQuery(query, repo, config);
      if ((versionables != null) && (versionables.length > 0))
      {
        jsonizeVersionables(jVersionables, versionables, scmService, out, repo, config, vMode);
        printVersionables(jVersionables, versionables, out, repo.getRepositoryURI(), config, vMode);
      }
    }
    catch (TeamRepositoryException e) {
      String msg = logAndCreateExceptionMessage(queryString, e);
      throw StatusHelper.failure(msg, null); }
    IVersionable[] versionables;
    if ((versionables == null) || (versionables.length == 0))
    {
      throw StatusHelper.itemNotFound(NLS.bind(Messages.QueryVersionableCmd_NO_ITEM_MATCH, queryString));
    }
  }
  
  private static void printVersionables(JSONArray versions, IVersionable[] versionables, IndentingPrintStream out, String repoUri, IScmClientConfiguration config, QueryCmd.VersionMode vMode) {
    boolean showVersionId = (vMode == QueryCmd.VersionMode.LONG_VERSION) || (vMode == QueryCmd.VersionMode.SHORT_VERSION);
    if (config.isJSONEnabled()) {
      JSONObject jResult = new JSONObject();
      jResult.put("versionables", versions);
      config.getContext().stdout().print(jResult);
      return;
    }
    
    for (Object obj : versions) {
      JSONObject jPath = (JSONObject)obj;
      String versionId = null;
      JSONObject version = (JSONObject)jPath.get("version");
      if (version != null) {
        switch (vMode) {
        case SHORT_VERSION: 
          versionId = JSONPrintUtil.jsonizeGetLongVersionId(version);
          break;
        case LONG_VERSION: 
          versionId = JSONPrintUtil.jsonizeGetShortVersionId(version);
        }
        
      }
      if (config.getAliasConfig().showUuid()) {
        if ((showVersionId) && (versionId != null)) {
          out.println(NLS.bind(Messages.QueryVersionableCmd_PRINT_UUID_VERSION, new String[] { (String)jPath.get("uuid"), 
            (String)jPath.get("state-id"), (String)jPath.get("name"), 
            versionId }));
        } else {
          out.println(NLS.bind(Messages.QueryVersionableCmd_PRINT_UUID, new String[] { (String)jPath.get("uuid"), 
            (String)jPath.get("state-id"), (String)jPath.get("name") }));
        }
      } else if ((showVersionId) && (versionId != null)) {
        out.println(NLS.bind(Messages.QueryVersionableCmd_PRINT_NAME_VERSION, new String[] { (String)jPath.get("name"), 
          versionId }));
      } else {
        out.println(NLS.bind(Messages.QueryVersionableCmd_PRINT_NAME, new String[] { (String)jPath.get("name") }));
      }
    }
  }
  
  private static void jsonizeVersionables(JSONArray versions, IVersionable[] versionables, IScmRichClientRestService scmService, IndentingPrintStream out, ITeamRepository repo, IScmClientConfiguration config, QueryCmd.VersionMode vMode)
    throws FileSystemException
  {
    if (versionables != null) {
      for (IVersionable versionable : versionables)
      {
        JSONObject jVersionable = JSONPrintUtil.jsonize(versionable.getName(), versionable.getItemId().getUuidValue(), repo.getRepositoryURI());
        jVersionable.put("item-type", versionable.getItemType().getName());
        jVersionable.put("state-id", versionable.getStateId().getUuidValue());
        
        if ((vMode != QueryCmd.VersionMode.HIDE) && (!versionable.getItemType().equals(IFolder.ITEM_TYPE))) {
          ScmVersionableIdentifierList versionIdentifiers = 
            RepoUtil.getVersionIdentifiers(
            scmService, 
            Collections.singletonList(versionable.getStateId().getUuidValue()), config);
          if (versionIdentifiers.getVersionableIdentifiers().size() == 1) {
            ScmVersionableIdentifier vid = 
              (ScmVersionableIdentifier)versionIdentifiers.getVersionableIdentifiers().get(0);
            jVersionable.put("version", JSONPrintUtil.jsonizeVersionId(vid));
          }
        }
        versions.add(jVersionable);
      }
    }
  }
}
