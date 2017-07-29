package com.ibm.team.filesystem.cli.client.internal.workspace;

import com.ibm.team.filesystem.cli.client.internal.Messages;
import com.ibm.team.filesystem.cli.core.subcommands.CommonOptions;
import com.ibm.team.filesystem.cli.core.subcommands.IScmClientConfiguration;
import com.ibm.team.filesystem.cli.core.subcommands.IScmCommandLineArgument;
import com.ibm.team.filesystem.cli.core.util.AliasUtil;
import com.ibm.team.filesystem.cli.core.util.CLIFileSystemClientException;
import com.ibm.team.filesystem.cli.core.util.JSONPrintUtil;
import com.ibm.team.filesystem.cli.core.util.RepoUtil;
import com.ibm.team.filesystem.cli.core.util.RepoUtil.ItemType;
import com.ibm.team.filesystem.cli.core.util.StatusHelper;
import com.ibm.team.filesystem.cli.core.util.SubcommandUtil;
import com.ibm.team.filesystem.client.FileSystemException;
import com.ibm.team.filesystem.client.rest.IFilesystemRestClient;
import com.ibm.team.filesystem.client.rest.parameters.ParmsWorkspace;
import com.ibm.team.filesystem.common.internal.rest.client.core.ReadScopeDTO;
import com.ibm.team.filesystem.common.internal.rest.client.core.WorkspaceDetailsDTO;
import com.ibm.team.filesystem.common.internal.rest.client.locks.ContributorNameDTO;
import com.ibm.team.process.common.IAccessGroup;
import com.ibm.team.process.common.IProjectArea;
import com.ibm.team.process.common.IProjectAreaHandle;
import com.ibm.team.process.common.ITeamArea;
import com.ibm.team.repository.client.ITeamRepository;
import com.ibm.team.repository.common.IAuditableHandle;
import com.ibm.team.repository.common.TeamRepositoryException;
import com.ibm.team.repository.common.UUID;
import com.ibm.team.repository.common.json.JSONArray;
import com.ibm.team.repository.common.json.JSONObject;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IClientConfiguration;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IExecutionContext;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IOptionSource;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.Options;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.PositionalOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.exceptions.ConflictingOptionException;
import com.ibm.team.rtc.cli.infrastructure.internal.util.IndentingPrintStream;
import com.ibm.team.scm.common.IWorkspace;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.osgi.util.NLS;

public class WorkspacePropertyListCmd
  extends WorkspacePropertyCmd
  implements IOptionSource
{
  public WorkspacePropertyListCmd() {}
  
  public Options getOptions()
    throws ConflictingOptionException
  {
    Options options = new Options(false);
    
    SubcommandUtil.addRepoLocationToOptions(options);
    options.addOption(new PositionalOptionDefinition(CommonOptions.OPT_WORKSPACE, "workspace", 1, -1, "@"), 
      Messages.WorkspacePropertiesCmdOptions_LIST_WORKSPACE_HELP)
      .addOption(CommonOptions.OPT_JSON, Messages.Common_JSON_OUTPUT);
    
    return options;
  }
  
  public void run() throws FileSystemException
  {
    initializeArgs(null);
    listProperties(null);
  }
  
  protected void listProperties(String propertyName) throws FileSystemException {
    List<String> propertyNames = null;
    if (propertyName != null) {
      propertyNames = new ArrayList(1);
      propertyNames.add(propertyName.toLowerCase());
    }
    listProperties(wsSelectorList, propertyName, propertyNames, client, config);
  }
  
  public static void listProperties(List<IScmCommandLineArgument> wsSelectorList, String propertyName, List<String> propertyNames, IFilesystemRestClient client, IScmClientConfiguration config) throws FileSystemException
  {
    JSONArray jWsArray = new JSONArray();
    for (IScmCommandLineArgument wsSelector : wsSelectorList) {
      JSONObject jWs = listProperties(wsSelector, propertyNames, client, config);
      jWsArray.add(jWs);
    }
    
    boolean retrievedWorkspaceProperties;
    boolean retrievedWorkspaceProperties;
    if (config.isJSONEnabled()) {
      if (!jWsArray.isEmpty()) {
        JSONObject jResult = new JSONObject();
        jResult.put("workspaces", jWsArray);
        config.getContext().stdout().print(jResult);
      }
      retrievedWorkspaceProperties = hasAllWorkspaceProperties(jWsArray);
    } else {
      retrievedWorkspaceProperties = printProperties(jWsArray, propertyName == null, config);
    }
    
    if (!retrievedWorkspaceProperties) {
      throw StatusHelper.propertiesUnavailable(Messages.WorkspacePropertiesCmd_PROPERTY_LIST_FAILURE);
    }
  }
  
  private static JSONObject listProperties(IScmCommandLineArgument wsSelector, List<String> propertyNames, IFilesystemRestClient client, IScmClientConfiguration config) throws FileSystemException
  {
    JSONObject jWs = new JSONObject();
    long statusCode = 0L;
    String errorMsg = null;
    ITeamRepository repo = null;
    IWorkspace ws = null;
    
    try
    {
      repo = RepoUtil.loginUrlArgAncestor(config, client, wsSelector);
      
      ws = RepoUtil.getWorkspace(wsSelector.getItemSelector(), true, true, repo, config);
      
      JSONObject jProps = getProperties(ws, propertyNames, repo, client, config);
      jWs.put("workspace", ws.getName());
      jWs.put("properties", jProps);
    } catch (CLIFileSystemClientException e) {
      statusCode = e.getStatus().getCode();
      errorMsg = e.getLocalizedMessage();
      StatusHelper.logException(errorMsg, e);
    } catch (TeamRepositoryException e) {
      CLIFileSystemClientException exp = StatusHelper.wrap("", e, new IndentingPrintStream(config.getContext().stderr()));
      statusCode = exp.getStatus().getCode();
      errorMsg = e.getLocalizedMessage();
      StatusHelper.logException(errorMsg, e);
    } catch (Exception e) {
      statusCode = 3L;
      errorMsg = e.getLocalizedMessage();
      StatusHelper.logException(errorMsg, e);
    }
    
    if (ws == null) {
      jWs.put("workspace", wsSelector.getItemSelector());
    }
    if (errorMsg != null) {
      jWs.put("error-message", errorMsg);
    }
    
    jWs.put("status-code", Long.valueOf(statusCode));
    
    return jWs;
  }
  
  protected static JSONObject getProperties(IWorkspace ws, List<String> propertyNames, ITeamRepository repo, IFilesystemRestClient client, IScmClientConfiguration config)
    throws FileSystemException
  {
    JSONObject jProps = new JSONObject();
    new IndentingPrintStream(config.getContext().stdout());
    

    ParmsWorkspace parmsWs = new ParmsWorkspace(repo.getRepositoryURI(), ws.getItemId().getUuidValue());
    WorkspaceDetailsDTO wsDetails = 
      (WorkspaceDetailsDTO)RepoUtil.getWorkspaceDetails(Collections.singletonList(parmsWs), client, config).get(0);
    

    jProps.put("name", wsDetails.getName());
    
    jProps.put("uuid", wsDetails.getItemId());
    
    jProps.put("url", wsDetails.getRepositoryURL());
    
    jProps.put("type", wsDetails.isStream() ? RepoUtil.ItemType.STREAM.toString() : RepoUtil.ItemType.WORKSPACE.toString());
    

    if ((wsDetails.isStream()) && ((propertyNames == null) || (propertyNames.contains("auto-lock-files")))) {
      jProps.put("auto-lock-files", wsDetails.getExclusiveFileLockPatterns());
    }
    else if ((!wsDetails.isStream()) && (propertyNames != null) && (propertyNames.contains("auto-lock-files"))) {
      throw StatusHelper.propertiesUnavailable(NLS.bind(Messages.Stream_ONLY_OPTION, "auto-lock-files"));
    }
    

    ContributorNameDTO streamLockOwner = wsDetails.getLockedBy();
    JSONPrintUtil.addJsonizedLockOwner(jProps, streamLockOwner);
    

    String ownerName = RepoUtil.getOwnerName(wsDetails.getOwner(), repo, config);
    if ((propertyNames == null) || (propertyNames.contains("ownedby")) || (propertyNames.contains("owned"))) {
      JSONObject jOwnedBy = new JSONObject();
      jOwnedBy.put("name", ownerName);
      jOwnedBy.put("uuid", wsDetails.getOwner().getItemId().getUuidValue());
      jOwnedBy.put("url", wsDetails.getRepositoryURL());
      jOwnedBy.put("type", SubcommandUtil.getItemType(wsDetails.getOwner().getItemType()).toString());
      jProps.put("ownedby", jOwnedBy);
    }
    

    if ((propertyNames == null) || (propertyNames.contains("visibility")) || (propertyNames.contains("visi"))) {
      JSONObject jVisibility = new JSONObject();
      JSONObject jInfo = null;
      
      if (wsDetails.getReadScope().getReadScope().equalsIgnoreCase("public_scope")) {
        jVisibility.put("type", "public");
      } else if (wsDetails.getReadScope().getReadScope().equalsIgnoreCase("private_scope")) {
        jVisibility.put("type", "private");
      } else if (wsDetails.getReadScope().getReadScope().equalsIgnoreCase("contributor_deferring_scope")) {
        jVisibility.put("type", "projectarea");
        String defersTo = RepoUtil.getOwnerName(wsDetails.getReadScope().getDefersTo(), repo, config);
        
        jInfo = new JSONObject();
        jInfo.put("name", defersTo);
        jInfo.put("uuid", wsDetails.getReadScope().getDefersTo().getItemId().getUuidValue());
        jInfo.put("url", wsDetails.getRepositoryURL());
        jInfo.put("type", SubcommandUtil.getItemType(wsDetails.getReadScope().getDefersTo().getItemType()).toString());
        jInfo.put("contributor-deferring", Boolean.TRUE);
      } else if (wsDetails.getReadScope().getReadScope().equalsIgnoreCase("process_area_scope")) {
        jVisibility.put("type", "projectarea");
        IProjectArea projArea = null;
        

        if (ITeamArea.ITEM_TYPE.equals(wsDetails.getOwner().getItemType())) {
          ITeamArea teamArea = (ITeamArea)RepoUtil.getItem(ITeamArea.ITEM_TYPE, wsDetails.getOwner().getItemId(), repo, 1, config);
          projArea = (IProjectArea)RepoUtil.getItem(IProjectArea.ITEM_TYPE, teamArea.getProjectArea().getItemId(), repo, config);
        } else if (IProjectArea.ITEM_TYPE.equals(wsDetails.getOwner().getItemType())) {
          projArea = (IProjectArea)RepoUtil.getItem(IProjectArea.ITEM_TYPE, wsDetails.getOwner().getItemId(), repo, 1, config);
        }
        
        if (projArea != null) {
          jInfo = new JSONObject();
          jInfo.put("name", projArea.getName());
          jInfo.put("uuid", projArea.getItemId().getUuidValue());
          jInfo.put("url", wsDetails.getRepositoryURL());
          jInfo.put("type", RepoUtil.ItemType.PROJECTAREA.toString());
        }
      } else if (wsDetails.getReadScope().getReadScope().equalsIgnoreCase("team_area_private_scope")) {
        jVisibility.put("type", "teamarea");
        jInfo = new JSONObject();
        jInfo.put("name", ownerName);
        jInfo.put("uuid", wsDetails.getOwner().getItemId().getUuidValue());
        jInfo.put("url", wsDetails.getRepositoryURL());
        jInfo.put("type", RepoUtil.ItemType.TEAMAREA.toString());
      } else if (wsDetails.getReadScope().getReadScope().equalsIgnoreCase("access_group_scope")) {
        jVisibility.put("type", "accessgroup");
        UUID accessGroupId = wsDetails.getReadScope().getDefersTo().getItemId();
        IAccessGroup accessGroup = RepoUtil.getAccessGroup(accessGroupId, repo, config);
        if (accessGroup != null) {
          jInfo = new JSONObject();
          jInfo.put("name", accessGroup.getName());
          jInfo.put("uuid", accessGroup.getGroupContextId().getUuidValue());
          jInfo.put("url", wsDetails.getRepositoryURL());
          jInfo.put("type", RepoUtil.ItemType.ACCESSGROUP.toString());
        }
      } else {
        jVisibility.put("type", "unknown");
      }
      
      if (jInfo != null) {
        jVisibility.put("info", jInfo);
      }
      jProps.put("visibility", jVisibility);
    }
    

    if ((propertyNames == null) || (propertyNames.contains("description")) || (propertyNames.contains("desc"))) {
      jProps.put("description", wsDetails.getDescription());
    }
    
    return jProps;
  }
  
  private static boolean printProperties(JSONArray jWsArray, boolean printCaption, IClientConfiguration config) throws FileSystemException
  {
    boolean retrievedAllWorkspaceProperties = true;
    IndentingPrintStream out = new IndentingPrintStream(config.getContext().stdout());
    IndentingPrintStream err = new IndentingPrintStream(config.getContext().stderr());
    
    for (Object obj : jWsArray) {
      JSONObject jWs = (JSONObject)obj;
      boolean result = printProperties(jWs, printCaption, out, err);
      if (!result) {
        retrievedAllWorkspaceProperties = false;
      }
    }
    
    return retrievedAllWorkspaceProperties;
  }
  

  public static boolean printProperties(JSONObject jWs, boolean printCaption, IndentingPrintStream out, IndentingPrintStream err)
  {
    String ws = (String)jWs.get("workspace");
    
    long statusCode = ((Long)jWs.get("status-code")).longValue();
    if (statusCode != 0L)
    {
      String errorMsg = (String)jWs.get("error-message");
      err.println(ws);
      err.indent().println(NLS.bind(Messages.Common_ERROR_CODE, Long.valueOf(statusCode)));
      err.indent().println(NLS.bind(Messages.Common_ERROR_MESSAGE, errorMsg));
      return false;
    }
    

    JSONObject jProps = (JSONObject)jWs.get("properties");
    
    String name = (String)jProps.get("name");
    String itemId = (String)jProps.get("uuid");
    String repoUri = (String)jProps.get("url");
    RepoUtil.ItemType itemType = RepoUtil.ItemType.valueOf((String)jProps.get("type"));
    String autoFileLockPatterns = (String)jProps.get("auto-lock-files");
    

    String wsTitle = AliasUtil.selector(name, UUID.valueOf(itemId), repoUri, itemType);
    out.println(wsTitle);
    

    if (printCaption) {
      out.indent().println(NLS.bind(Messages.WorkspacePropertiesCmd_REPOSITORY, repoUri));
    }
    

    JSONObject jOwnedBy = (JSONObject)jProps.get("ownedby");
    if (jOwnedBy != null) {
      String ownedbyName = (String)jOwnedBy.get("name");
      String ownedbyUuid = (String)jOwnedBy.get("uuid");
      String ownedbyUri = (String)jOwnedBy.get("url");
      RepoUtil.ItemType ownedbyType = RepoUtil.ItemType.valueOf((String)jOwnedBy.get("type"));
      
      String ownedbyInfo = AliasUtil.selector(ownedbyName, UUID.valueOf(ownedbyUuid), ownedbyUri, ownedbyType);
      out.indent().println(printCaption ? NLS.bind(Messages.WorkspacePropertiesCmd_OWNEDBY, ownedbyInfo) : ownedbyInfo);
    }
    

    JSONObject jVisibility = (JSONObject)jProps.get("visibility");
    if (jVisibility != null) {
      String visibilityType = (String)jVisibility.get("type");
      String visibilityInfo = Messages.Common_UNKNOWN;
      if (visibilityType.equals("public")) {
        visibilityInfo = Messages.Common_PUBLIC;
      } else if (visibilityType.equals("private")) {
        visibilityInfo = Messages.Common_PRIVATE;
      } else if (visibilityType.equals("accessgroup")) {
        JSONObject jInfo = (JSONObject)jVisibility.get("info");
        if (jInfo != null) {
          String infoName = (String)jInfo.get("name");
          String infoUuid = (String)jInfo.get("uuid");
          String infoUri = (String)jInfo.get("url");
          RepoUtil.ItemType infoType = RepoUtil.ItemType.valueOf((String)jInfo.get("type"));
          
          String selector = AliasUtil.selector(infoName, UUID.valueOf(infoUuid), infoUri, infoType);
          visibilityInfo = NLS.bind(Messages.WorkspacePropertiesCmd_ACCESS_GROUP, selector);
        } else {
          visibilityInfo = NLS.bind(Messages.WorkspacePropertiesCmd_ACCESS_GROUP, "unknown");
        }
      } else if ((visibilityType.equals("projectarea")) || (visibilityType.equals("teamarea"))) {
        JSONObject jInfo = (JSONObject)jVisibility.get("info");
        if (jInfo != null) {
          String infoName = (String)jInfo.get("name");
          String infoUuid = (String)jInfo.get("uuid");
          String infoUri = (String)jInfo.get("url");
          RepoUtil.ItemType infoType = RepoUtil.ItemType.valueOf((String)jInfo.get("type"));
          
          visibilityInfo = AliasUtil.selector(infoName, UUID.valueOf(infoUuid), infoUri, infoType);
          if (jInfo.get("contributor-deferring") != null) {
            visibilityInfo = NLS.bind(Messages.WorkspacePropertiesCmd_PROJECT_SCOPED, visibilityInfo);
          }
        }
      }
      
      out.indent().println(printCaption ? NLS.bind(Messages.WorkspacePropertiesCmd_VISIBILITY, visibilityInfo) : visibilityInfo);
    }
    

    String description = (String)jProps.get("description");
    if ((description != null) && (!description.isEmpty())) {
      if (printCaption) {
        out.indent().println(Messages.WorkspacePropertiesCmd_DESCRIPTION);
      }
      out.indent().println(description);
    }
    

    if (autoFileLockPatterns != null) {
      out.indent().println(printCaption ? NLS.bind(Messages.WorkspacePropertiesCmd_AUTO_FILE_LOCK_PATTERNS, autoFileLockPatterns) : autoFileLockPatterns);
    }
    
    String lockedby = JSONPrintUtil.getLockOwnerName(jProps);
    if (lockedby != null) {
      out.indent().println(printCaption ? NLS.bind(Messages.WorkspacePropertyListCmd_0, lockedby) : lockedby);
    }
    
    return true;
  }
}
