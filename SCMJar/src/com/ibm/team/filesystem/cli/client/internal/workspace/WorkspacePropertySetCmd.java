package com.ibm.team.filesystem.cli.client.internal.workspace;

import com.ibm.team.filesystem.cli.client.internal.Messages;
import com.ibm.team.filesystem.cli.core.internal.ScmCommandLineArgument;
import com.ibm.team.filesystem.cli.core.subcommands.CommonOptions;
import com.ibm.team.filesystem.cli.core.subcommands.IScmClientConfiguration;
import com.ibm.team.filesystem.cli.core.subcommands.IScmCommandLineArgument;
import com.ibm.team.filesystem.cli.core.util.AliasUtil;
import com.ibm.team.filesystem.cli.core.util.CLIFileSystemClientException;
import com.ibm.team.filesystem.cli.core.util.RepoUtil;
import com.ibm.team.filesystem.cli.core.util.RepoUtil.ItemType;
import com.ibm.team.filesystem.cli.core.util.StatusHelper;
import com.ibm.team.filesystem.cli.core.util.SubcommandUtil;
import com.ibm.team.filesystem.client.FileSystemException;
import com.ibm.team.filesystem.client.rest.IFilesystemRestClient;
import com.ibm.team.filesystem.client.rest.parameters.ParmsPutWorkspace;
import com.ibm.team.filesystem.client.rest.parameters.ParmsReadScope;
import com.ibm.team.filesystem.client.rest.parameters.ParmsWorkspace;
import com.ibm.team.filesystem.common.internal.rest.client.core.ReadScopeDTO;
import com.ibm.team.filesystem.common.internal.rest.client.core.WorkspaceDetailsDTO;
import com.ibm.team.process.common.IAccessGroup;
import com.ibm.team.process.common.IProcessArea;
import com.ibm.team.process.common.IProjectArea;
import com.ibm.team.process.common.IProjectAreaHandle;
import com.ibm.team.process.common.ITeamArea;
import com.ibm.team.repository.client.ITeamRepository;
import com.ibm.team.repository.common.IAuditableHandle;
import com.ibm.team.repository.common.IContributor;
import com.ibm.team.repository.common.IItemType;
import com.ibm.team.repository.common.TeamRepositoryException;
import com.ibm.team.repository.common.UUID;
import com.ibm.team.repository.common.json.JSONArray;
import com.ibm.team.repository.common.json.JSONObject;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IClientConfiguration;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IExecutionContext;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IOptionSource;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.ICommandLine;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.NamedOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.Options;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.PositionalOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.exceptions.ConflictingOptionException;
import com.ibm.team.rtc.cli.infrastructure.internal.util.IndentingPrintStream;
import com.ibm.team.scm.common.IWorkspace;
import java.io.PrintStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.osgi.util.NLS;





public class WorkspacePropertySetCmd
  extends WorkspacePropertyCmd
  implements IOptionSource
{
  public WorkspacePropertySetCmd() {}
  
  public static final PositionalOptionDefinition OPT_PROPERTY_NAME = new PositionalOptionDefinition("property-name", 1, 1);
  public static final PositionalOptionDefinition OPT_PROPERTY_VALUE = new PositionalOptionDefinition("property-value", 1, 1);
  public static final NamedOptionDefinition OPT_PROJECT_IDENTIFIER = new NamedOptionDefinition("p", "process-area", 1);
  
  public Options getOptions() throws ConflictingOptionException {
    Options options = new Options(false);
    
    SubcommandUtil.addRepoLocationToOptions(options);
    options.addOption(OPT_PROPERTY_NAME, NLS.bind(Messages.WorkspacePropertiesCmdOptions_OPT_PROPERTY_SET_NAME_HELP, 
      new String[] { "name", "ownedby", "owned", "visibility", "visi", 
      "description", "desc", "auto-lock-files" }))
      .addOption(OPT_PROPERTY_VALUE, NLS.bind(Messages.WorkspacePropertiesCmdOptions_OPT_PROPERTY_VALUE_HELP, 
      new String[] { "visibility", "public", "private", "teamarea", "projectarea" }))
      .addOption(new PositionalOptionDefinition(CommonOptions.OPT_WORKSPACE, "workspace", 1, -1, "@"), 
      Messages.WorkspacePropertiesCmdOptions_SET_WORKSPACE_HELP)
      .addOption(OPT_PROJECT_IDENTIFIER, NLS.bind(Messages.WorkspacePropertiesCmdOptions_OPT_PROJECT_IDENTIFIER_HELP, 
      new String[] { "visibility", "teamarea", "projectarea" }))
      .addOption(CommonOptions.OPT_JSON, Messages.Common_JSON_OUTPUT);
    
    return options;
  }
  
  public void run() throws FileSystemException {
    ICommandLine cli = config.getSubcommandCommandLine();
    String propertyName = cli.getOption(OPT_PROPERTY_NAME, null);
    IScmCommandLineArgument propertyValue = ScmCommandLineArgument.create(cli.getOptionValue(OPT_PROPERTY_VALUE, null), config);
    IScmCommandLineArgument projectSelector = ScmCommandLineArgument.create(cli.getOptionValue(OPT_PROJECT_IDENTIFIER, null), config);
    
    initializeArgs(propertyName);
    Map<String, IScmCommandLineArgument> properties = new HashMap();
    properties.put(propertyName.toLowerCase(), propertyValue);
    
    setProperties(wsSelectorList, properties, projectSelector, client, config);
  }
  
  public static void setProperties(List<IScmCommandLineArgument> wsSelectorList, Map<String, IScmCommandLineArgument> properties, IScmCommandLineArgument visibilitySelector, IFilesystemRestClient client, IScmClientConfiguration config) throws FileSystemException
  {
    JSONArray jWsArray = new JSONArray();
    for (IScmCommandLineArgument wsSelector : wsSelectorList) {
      JSONObject jWs = setProperties(wsSelector, properties, visibilitySelector, client, config);
      jWsArray.add(jWs);
    }
    boolean setAllWorkspaceProperties;
    boolean setAllWorkspaceProperties;
    if (config.isJSONEnabled()) {
      if (!jWsArray.isEmpty()) {
        JSONObject jResult = new JSONObject();
        jResult.put("workspaces", jWsArray);
        config.getContext().stdout().print(jResult);
      }
      setAllWorkspaceProperties = hasAllWorkspaceProperties(jWsArray);
    } else {
      setAllWorkspaceProperties = printResult(jWsArray, config);
      if (setAllWorkspaceProperties) {
        config.getContext().stdout().println(Messages.WorkspacePropertiesCmd_PROPERTY_SET_SUCCESS);
      }
    }
    
    if (!setAllWorkspaceProperties) {
      throw StatusHelper.propertiesUnavailable(Messages.WorkspacePropertiesCmd_PROPERTY_SET_FAILURE);
    }
  }
  

  private static JSONObject setProperties(IScmCommandLineArgument wsSelector, Map<String, IScmCommandLineArgument> properties, IScmCommandLineArgument visibilitySelector, IFilesystemRestClient client, IScmClientConfiguration config)
    throws FileSystemException
  {
    JSONObject jWs = new JSONObject();
    String errorMsg = null;
    long statusCode = 0L;
    ITeamRepository repo = null;
    IWorkspace ws = null;
    boolean success = false;
    
    try
    {
      repo = RepoUtil.loginUrlArgAncestor(config, client, wsSelector);
      
      ws = RepoUtil.getWorkspace(wsSelector.getItemSelector(), true, true, repo, config);
      jWs.put("workspace", ws.getName());
      jWs.put("uuid", ws.getItemId().getUuidValue());
      jWs.put("url", repo.getRepositoryURI());
      jWs.put("type", ws.isStream() ? RepoUtil.ItemType.STREAM.toString() : RepoUtil.ItemType.WORKSPACE.toString());
      
      setProperties(ws, properties, visibilitySelector, repo, client, config);
      success = true;
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
    
    jWs.put("status-code", Long.valueOf(statusCode));
    if (!success) {
      jWs.put("error-message", errorMsg);
    }
    
    StringBuffer propertyNames = new StringBuffer();
    for (String property : properties.keySet()) {
      if (propertyNames.length() != 0) {
        propertyNames.append(", ");
      }
      propertyNames.append(property);
    }
    jWs.put("property", propertyNames.toString());
    
    return jWs;
  }
  












  protected static void setProperties(IWorkspace ws, Map<String, IScmCommandLineArgument> properties, IScmCommandLineArgument visibilitySelector, ITeamRepository repo, IFilesystemRestClient client, IScmClientConfiguration config)
    throws FileSystemException
  {
    ParmsPutWorkspace parms = new ParmsPutWorkspace();
    workspace = new ParmsWorkspace(repo.getRepositoryURI(), ws.getItemId().getUuidValue());
    
    if (properties.containsKey("name")) {
      name = ((IScmCommandLineArgument)properties.get("name")).getItemSelector();
    }
    
    IProcessArea owningProcessArea = null;
    if ((properties.containsKey("ownedby")) || (properties.containsKey("owned"))) {
      IScmCommandLineArgument propertyValue = (IScmCommandLineArgument)properties.get("ownedby");
      if (propertyValue == null) {
        propertyValue = (IScmCommandLineArgument)properties.get("owned");
      }
      
      if (ws.isStream())
      {
        SubcommandUtil.validateArgument(propertyValue, new RepoUtil.ItemType[] { RepoUtil.ItemType.PROJECTAREA, RepoUtil.ItemType.TEAMAREA });
        

        owningProcessArea = RepoUtil.getProcessArea(propertyValue, null, repo, config);
        if (owningProcessArea == null) {
          throw StatusHelper.inappropriateArgument(NLS.bind(Messages.WorkspacePropertiesCmd_STREAM_OWNEDBY_ERROR, "ownedby", propertyValue.getItemSelector()));
        }
        
        newOwnerItemId = owningProcessArea.getItemId().getUuidValue();
        newOwnerItemType = owningProcessArea.getItemType().getName();
        

        readScope = new ParmsReadScope();
        readScope.scope = "process_area_scope";
      }
      else {
        IContributor contributor = RepoUtil.getContributor(propertyValue.getItemSelector(), repo, config);
        newOwnerItemId = contributor.getItemId().getUuidValue();
        newOwnerItemType = IContributor.ITEM_TYPE.getName();
      }
    }
    
    if ((properties.containsKey("visibility")) || (properties.containsKey("visi"))) {
      IScmCommandLineArgument propertyValue = (IScmCommandLineArgument)properties.get("visibility");
      if (propertyValue == null) {
        propertyValue = (IScmCommandLineArgument)properties.get("visi");
      }
      

      WorkspaceDetailsDTO wsDetails = null;
      if ((propertyValue.getItemSelector().equalsIgnoreCase("teamarea")) || (propertyValue.getItemSelector().equalsIgnoreCase("projectarea"))) {
        ParmsWorkspace parmsWs = new ParmsWorkspace(repo.getRepositoryURI(), ws.getItemId().getUuidValue());
        wsDetails = (WorkspaceDetailsDTO)RepoUtil.getWorkspaceDetails(Collections.singletonList(parmsWs), client, config).get(0);
      }
      
      IItemType ownerType = IContributor.ITEM_TYPE;
      if (newOwnerItemType != null) {
        ownerType = SubcommandUtil.getOwnerType(newOwnerItemType);
      } else if (wsDetails != null) {
        ownerType = wsDetails.getOwner().getItemType();
      } else {
        ownerType = IContributor.ITEM_TYPE;
      }
      
      IAuditableHandle owner = null;
      if (owningProcessArea != null) {
        owner = owningProcessArea;
      } else if (wsDetails != null) {
        owner = wsDetails.getOwner();
      }
      


      readScope = new ParmsReadScope();
      if (propertyValue.getItemSelector().equalsIgnoreCase("public")) {
        if (ws.isStream()) {
          throw StatusHelper.argSyntax(NLS.bind(Messages.WorkspacePropertiesCmd_INVALID_STREAM_PROPERTY_VALUE, 
            new String[] { "public", "projectarea", "teamarea" }));
        }
        readScope.scope = "public_scope";
      } else if (propertyValue.getItemSelector().equalsIgnoreCase("private")) {
        if (ws.isStream()) {
          throw StatusHelper.argSyntax(NLS.bind(Messages.WorkspacePropertiesCmd_INVALID_STREAM_PROPERTY_VALUE, 
            new String[] { "private", "projectarea", "teamarea" }));
        }
        readScope.scope = "private_scope";
      } else if (propertyValue.getItemSelector().equalsIgnoreCase("teamarea")) {
        if (!ws.isStream()) {
          throw StatusHelper.inappropriateArgument(NLS.bind(Messages.WorkspacePropertiesCmd_INVALID_WS_PROPERTY_VALUE, 
            new String[] { "teamarea", "public", "private", "projectarea" }));
        }
        
        if (IProjectArea.ITEM_TYPE.equals(ownerType)) {
          throw StatusHelper.inappropriateArgument(Messages.WorkspacePropertiesCmd_SPECIFIY_PROJECTAREA);
        }
        
        SubcommandUtil.validateArgument(visibilitySelector, RepoUtil.ItemType.TEAMAREA);
        


        IProcessArea processArea = null;
        if (visibilitySelector != null) {
          processArea = RepoUtil.getProcessArea(visibilitySelector, RepoUtil.ItemType.TEAMAREA, repo, config);
          if (processArea == null) {
            throw StatusHelper.inappropriateArgument(NLS.bind(Messages.ListCmd_TeamAreaNotFound, visibilitySelector.getItemSelector()));
          }
        }
        

        if ((visibilitySelector != null) && (!processArea.sameItemId(owner))) {
          throw StatusHelper.inappropriateArgument(NLS.bind(Messages.WorkspacePropertiesCmd_TEAMAREA_ALREADY_SET, 
            AliasUtil.selector(processArea.getName(), processArea.getItemId(), repo.getRepositoryURI(), RepoUtil.ItemType.TEAMAREA)));
        }
        if (wsDetails.getReadScope().getReadScope().equalsIgnoreCase("team_area_private_scope")) {
          return;
        }
        readScope.scope = "team_area_private_scope";

      }
      else if (propertyValue.getItemSelector().equalsIgnoreCase("projectarea"))
      {

        IProcessArea processArea = null;
        if (visibilitySelector != null) {
          SubcommandUtil.validateArgument(visibilitySelector, RepoUtil.ItemType.PROJECTAREA);
          processArea = RepoUtil.getProcessArea(visibilitySelector, RepoUtil.ItemType.PROJECTAREA, repo, config);
          if (processArea == null) {
            throw StatusHelper.inappropriateArgument(NLS.bind(Messages.ListCmd_NOPROJECTAREA, visibilitySelector.getItemSelector()));
          }
        }
        
        if (!ws.isStream())
        {
          if (visibilitySelector == null) {
            throw StatusHelper.argSyntax(Messages.WorkspacePropertiesCmd_SPECIFY_PROJECT_IDENTIFIER);
          }
          
          readScope.scope = "contributor_deferring_scope";
          readScope.defer_to = processArea.getItemId().getUuidValue();
        } else {
          readScope.scope = "process_area_scope";
          

          if (IProjectArea.ITEM_TYPE.equals(ownerType)) {
            if ((visibilitySelector != null) && (!processArea.sameItemId(owner))) {
              String ownerName = RepoUtil.getOwnerName(wsDetails.getOwner(), repo, config);
              throw StatusHelper.inappropriateArgument(NLS.bind(Messages.WorkspacePropertiesCmd_PROJECTAREA_ALREADY_SET, 
                AliasUtil.selector(ownerName, owner.getItemId(), wsDetails.getRepositoryURL(), RepoUtil.ItemType.PROJECTAREA)));
            }
          } else if (processArea != null)
          {
            ITeamArea teamArea = (ITeamArea)RepoUtil.getItem(ITeamArea.ITEM_TYPE, owner.getItemId(), repo, 1, config);
            IProjectArea projArea = (IProjectArea)RepoUtil.getItem(IProjectArea.ITEM_TYPE, teamArea.getProjectArea().getItemId(), repo, config);
            
            if (!projArea.getItemId().equals(processArea.getItemId())) {
              throw StatusHelper.inappropriateArgument(Messages.WorkspacePropertiesCmd_PROJECTAREA_INVALID);
            }
          }
        }
      } else if (propertyValue.getItemSelector().equalsIgnoreCase("accessgroup")) {
        if (visibilitySelector == null) {
          throw StatusHelper.argSyntax(Messages.WorkspacePropertiesCmd_SPECIFY_ACCESS_GROUP);
        }
        
        SubcommandUtil.validateArgument(visibilitySelector, RepoUtil.ItemType.ACCESSGROUP);
        IAccessGroup accessGroup = RepoUtil.getAccessGroup(visibilitySelector, repo, config);
        if (accessGroup == null) {
          throw StatusHelper.argSyntax(NLS.bind(Messages.WorkspacePropertiesCmd_INVALID_ACCESS_GROUP, visibilitySelector));
        }
        readScope.scope = "access_group_scope";
        readScope.defer_to = accessGroup.getGroupContextId().getUuidValue();
      }
      else {
        throw StatusHelper.argSyntax(NLS.bind(Messages.WorkspacePropertiesCmd_INVALID_PROPERTY_VALUE, propertyValue.getItemSelector()));
      }
    }
    
    if ((properties.containsKey("description")) || (properties.containsKey("desc"))) {
      IScmCommandLineArgument propertyValue = (IScmCommandLineArgument)properties.get("description");
      if (propertyValue == null) {
        propertyValue = (IScmCommandLineArgument)properties.get("desc");
      }
      
      description = propertyValue.getItemSelector();
    }
    

    if (properties.containsKey("auto-lock-files")) {
      IScmCommandLineArgument propertyValue = (IScmCommandLineArgument)properties.get("auto-lock-files");
      
      if (propertyValue != null) {
        if (!ws.isStream()) {
          throw StatusHelper.propertiesUnavailable(NLS.bind(Messages.Stream_ONLY_OPTION, "auto-lock-files"));
        }
        
        exclusiveFileLockPatterns = propertyValue.getItemSelector();
      }
    }
    


    try
    {
      client.postPutWorkspace(parms, null);
    } catch (TeamRepositoryException e) {
      throw StatusHelper.wrap(Messages.WorkspacePropertiesCmd_PROPERTY_SET_FAILURE, e, 
        new IndentingPrintStream(config.getContext().stderr()), repo.getRepositoryURI());
    }
  }
  
  private static boolean printResult(JSONArray jWsArray, IClientConfiguration config) {
    IndentingPrintStream err = new IndentingPrintStream(config.getContext().stderr());
    boolean setAllWorkspaceProperties = true;
    
    for (Object obj : jWsArray) {
      JSONObject jWs = (JSONObject)obj;
      

      String ws = (String)jWs.get("workspace");
      if (jWs.get("uuid") != null) {
        String wsItemId = (String)jWs.get("uuid");
        String repoUri = (String)jWs.get("url");
        RepoUtil.ItemType itemType = RepoUtil.ItemType.valueOf((String)jWs.get("type"));
        ws = AliasUtil.selector(ws, UUID.valueOf(wsItemId), repoUri, itemType);
      }
      
      long statusCode = ((Long)jWs.get("status-code")).longValue();
      if (statusCode != 0L)
      {
        String errorMsg = (String)jWs.get("error-message");
        err.println(ws);
        err.indent().println(NLS.bind(Messages.Common_ERROR_CODE, Long.valueOf(statusCode)));
        err.indent().println(NLS.bind(Messages.Common_ERROR_MESSAGE, errorMsg));
        setAllWorkspaceProperties = false;
      }
    }
    

    return setAllWorkspaceProperties;
  }
}
