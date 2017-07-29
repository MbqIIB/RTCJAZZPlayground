package com.ibm.team.filesystem.cli.client.internal.property;

import com.ibm.team.filesystem.cli.client.AbstractSubcommand;
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
import com.ibm.team.filesystem.client.rest.parameters.ParmsPutComponent;
import com.ibm.team.filesystem.client.rest.parameters.ParmsReadScope;
import com.ibm.team.process.common.IAccessGroup;
import com.ibm.team.process.common.IProcessArea;
import com.ibm.team.process.common.IProjectArea;
import com.ibm.team.process.common.IProjectAreaHandle;
import com.ibm.team.process.common.ITeamArea;
import com.ibm.team.repository.client.ITeamRepository;
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
import com.ibm.team.scm.common.IComponent;
import com.ibm.team.scm.common.internal.rich.rest.dto.ScmComponent2;
import com.ibm.team.scm.common.internal.rich.rest.dto.ScmHandle;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.osgi.util.NLS;




public class ComponentPropertySetCmd
  extends AbstractSubcommand
  implements IOptionSource
{
  IFilesystemRestClient client;
  
  public ComponentPropertySetCmd() {}
  
  public static final PositionalOptionDefinition OPT_PROPERTY_NAME = new PositionalOptionDefinition("property-name", 1, 1);
  public static final PositionalOptionDefinition OPT_PROPERTY_VALUE = new PositionalOptionDefinition("property-value", 1, 1);
  public static final NamedOptionDefinition OPT_SCOPE_TO_TEAMAREA = new NamedOptionDefinition("s", "teamarea-scope", 0);
  
  public Options getOptions() throws ConflictingOptionException {
    Options options = new Options(false);
    
    SubcommandUtil.addRepoLocationToOptions(options);
    options.addOption(OPT_PROPERTY_NAME, NLS.bind(Messages.ComponentPropertiesCmdOptions_OPT_PROPERTY_SET_NAME_HELP, 
      new String[] { "name", "ownedby", "owned", 
      "visibility", "visi" }))
      .addOption(OPT_PROPERTY_VALUE, NLS.bind(Messages.ComponentPropertiesCmdOptions_OPT_PROPERTY_VALUE_HELP, 
      new String[] { "visibility", "public", "private" }))
      .addOption(new PositionalOptionDefinition(CommonOptions.OPT_COMPONENTS_SELECTOR, "component", 1, -1, "@"), 
      Messages.ComponentPropertiesCmdOptions_COMPONENT_SET_HELP)
      .addOption(OPT_SCOPE_TO_TEAMAREA, Messages.ComponentPropertiesCmdOptions_OPT_SCOPE_TO_TEAMAREA_HELP)
      .addOption(CommonOptions.OPT_JSON, Messages.Common_JSON_OUTPUT);
    
    return options;
  }
  
  public void run() throws FileSystemException {
    ICommandLine cli = config.getSubcommandCommandLine();
    config.setEnableJSON(cli.hasOption(CommonOptions.OPT_JSON));
    
    String propertyName = cli.getOption(OPT_PROPERTY_NAME, null);
    if ((propertyName != null) && (!Arrays.asList(ComponentPropertyListCmd.PROPERTIES).contains(propertyName))) {
      throw StatusHelper.argSyntax(NLS.bind(Messages.WorkspacePropertiesCmd_INVALID_PROPERTY_NAME, propertyName));
    }
    
    IScmCommandLineArgument propertyValue = ScmCommandLineArgument.create(cli.getOptionValue(OPT_PROPERTY_VALUE, null), config);
    List<IScmCommandLineArgument> compSelectorList = ScmCommandLineArgument.createList(cli.getOptionValues(CommonOptions.OPT_COMPONENTS_SELECTOR), config);
    

    client = SubcommandUtil.setupDaemon(config);
    
    Map<String, IScmCommandLineArgument> properties = new HashMap();
    properties.put(propertyName.toLowerCase(), propertyValue);
    
    setProperties(compSelectorList, properties, cli.hasOption(OPT_SCOPE_TO_TEAMAREA), client, config);
  }
  
  public static void setProperties(List<IScmCommandLineArgument> compSelectorList, Map<String, IScmCommandLineArgument> properties, boolean teamAreaScope, IFilesystemRestClient client, IScmClientConfiguration config)
    throws FileSystemException
  {
    JSONArray jCompArray = new JSONArray();
    
    for (IScmCommandLineArgument compSelector : compSelectorList) {
      JSONObject jComp = setProperties(compSelector, properties, teamAreaScope, client, config);
      jCompArray.add(jComp);
    }
    boolean setAllComponentProperties;
    boolean setAllComponentProperties;
    if (config.isJSONEnabled()) {
      if (!jCompArray.isEmpty()) {
        JSONObject jResult = new JSONObject();
        jResult.put("components", jCompArray);
        config.getContext().stdout().print(jResult);
      }
      setAllComponentProperties = ComponentPropertyListCmd.hasAllComponentProperties(jCompArray);
    } else {
      setAllComponentProperties = printResult(jCompArray, config);
      if (setAllComponentProperties) {
        config.getContext().stdout().println(Messages.ComponentPropertiesCmd_PROPERTY_SET_SUCCESS);
      }
    }
    
    if (!setAllComponentProperties) {
      throw StatusHelper.propertiesUnavailable(Messages.ComponentPropertiesCmd_PROPERTY_SET_FAILURE);
    }
  }
  

  private static JSONObject setProperties(IScmCommandLineArgument compSelector, Map<String, IScmCommandLineArgument> properties, boolean teamAreaScope, IFilesystemRestClient client, IScmClientConfiguration config)
    throws FileSystemException
  {
    JSONObject jComp = new JSONObject();
    String errorMsg = null;
    long statusCode = 0L;
    ITeamRepository repo = null;
    IComponent comp = null;
    boolean success = false;
    
    try
    {
      repo = RepoUtil.loginUrlArgAncestor(config, client, compSelector);
      
      comp = RepoUtil.getComponent(compSelector.getItemSelector(), repo, config);
      jComp.put("component", comp.getName());
      jComp.put("uuid", comp.getItemId().getUuidValue());
      jComp.put("url", repo.getRepositoryURI());
      
      setProperties(comp, properties, teamAreaScope, repo, client, config);
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
    
    if (comp == null) {
      jComp.put("component", compSelector.getItemSelector());
    }
    
    jComp.put("status-code", Long.valueOf(statusCode));
    if (!success) {
      jComp.put("error-message", errorMsg);
    }
    
    StringBuffer propertyNames = new StringBuffer();
    for (String property : properties.keySet()) {
      if (propertyNames.length() != 0) {
        propertyNames.append(", ");
      }
      propertyNames.append(property);
    }
    jComp.put("property", properties.toString());
    
    return jComp;
  }
  










  protected static void setProperties(IComponent comp, Map<String, IScmCommandLineArgument> properties, boolean teamareaScope, ITeamRepository repo, IFilesystemRestClient client, IScmClientConfiguration config)
    throws FileSystemException
  {
    ParmsPutComponent parms = new ParmsPutComponent();
    componentItemId = comp.getItemId().getUuidValue();
    repositoryUrl = repo.getRepositoryURI();
    

    ScmComponent2 compInfo = RepoUtil.getComponentById(comp.getItemId().getUuidValue(), repo, config);
    
    if (properties.containsKey("name")) {
      name = ((IScmCommandLineArgument)properties.get("name")).getItemSelector();
    }
    
    if ((properties.containsKey("ownedby")) || 
      (properties.containsKey("owned"))) {
      IScmCommandLineArgument propertyValue = (IScmCommandLineArgument)properties.get("ownedby");
      if (propertyValue == null) {
        propertyValue = (IScmCommandLineArgument)properties.get("owned");
      }
      

      SubcommandUtil.validateArgument(propertyValue, new RepoUtil.ItemType[] { RepoUtil.ItemType.CONTRIBUTOR, RepoUtil.ItemType.PROJECTAREA, RepoUtil.ItemType.TEAMAREA });
      readScope = new ParmsReadScope();
      

      IContributor contributor = null;
      try {
        contributor = RepoUtil.getContributor(propertyValue.getItemSelector(), repo, config);
        newOwnerItemId = contributor.getItemId().getUuidValue();
        newOwnerItemType = IContributor.ITEM_TYPE.getName();
        readScope.scope = "private_scope";
      }
      catch (CLIFileSystemClientException localCLIFileSystemClientException) {}
      

      if (contributor == null)
      {
        IProcessArea processArea = RepoUtil.getProcessArea(propertyValue, null, repo, config);
        if (processArea == null) {
          throw StatusHelper.inappropriateArgument(NLS.bind(Messages.ComponentPropertiesCmd_OWNEDBY_ERROR, 
            "ownedby", propertyValue.getItemSelector()));
        }
        
        newOwnerItemId = processArea.getItemId().getUuidValue();
        newOwnerItemType = processArea.getItemType().getName();
        

        if ((teamareaScope) && (ITeamArea.ITEM_TYPE.equals(processArea.getItemType()))) {
          readScope.scope = "team_area_private_scope";
        } else {
          readScope.scope = "process_area_scope";
        }
      }
    }
    

    if ((properties.containsKey("visibility")) || 
      (properties.containsKey("visi"))) {
      IScmCommandLineArgument propertyValue = (IScmCommandLineArgument)properties.get("visibility");
      if (propertyValue == null) {
        propertyValue = (IScmCommandLineArgument)properties.get("visi");
      }
      
      IItemType ownerType = SubcommandUtil.getOwnerType(newOwnerItemType != null ? newOwnerItemType : compInfo.getOwner().getItemType());
      String ownerId = newOwnerItemId != null ? newOwnerItemId : compInfo.getOwner().getItemId();
      readScope = new ParmsReadScope();
      
      if (propertyValue.getItemSelector().equalsIgnoreCase("public")) {
        if (IContributor.ITEM_TYPE.equals(ownerType)) {
          readScope.scope = "public_scope";
        } else {
          throw StatusHelper.argSyntax(NLS.bind(Messages.ComponentPropertiesCmd_INVALID_VISIBILITY, 
            new String[] { "public", "projectarea", 
            "teamarea", "accessgroup" }));
        }
      } else if (propertyValue.getItemSelector().equalsIgnoreCase("private")) {
        readScope.scope = "private_scope";
        if (IContributor.ITEM_TYPE.equals(ownerType)) {
          readScope.scope = "private_scope";
        } else {
          throw StatusHelper.argSyntax(NLS.bind(Messages.ComponentPropertiesCmd_INVALID_VISIBILITY, 
            new String[] { "private", "projectarea", 
            "teamarea", "accessgroup" }));
        }
      } else if (propertyValue.getItemSelector().equalsIgnoreCase("teamarea")) {
        if (IContributor.ITEM_TYPE.equals(ownerType))
          throw StatusHelper.inappropriateArgument(Messages.ComponentPropertiesCmd_CANNOT_SET_TEAM_AREA_VISIBILITY);
        if (IProjectArea.ITEM_TYPE.equals(ownerType)) {
          throw StatusHelper.inappropriateArgument(Messages.ComponentPropertiesCmd_SPECIFIY_PROJECTAREA);
        }
        

        IScmCommandLineArgument teamSelector = (IScmCommandLineArgument)properties.get("processarea");
        if (teamSelector != null) {
          ITeamArea processArea = (ITeamArea)RepoUtil.getProcessArea(teamSelector, RepoUtil.ItemType.TEAMAREA, repo, config);
          if (processArea == null) {
            throw StatusHelper.inappropriateArgument(NLS.bind(Messages.ListCmd_TeamAreaNotFound, teamSelector.getItemSelector()));
          }
          if (!processArea.getItemId().getUuidValue().equals(ownerId)) {
            throw StatusHelper.inappropriateArgument(NLS.bind(Messages.ComponentPropertiesCmd_TEAMAREA_ALREADY_SET, teamSelector.getItemSelector()));
          }
        }
        readScope.scope = "team_area_private_scope";
      } else if (propertyValue.getItemSelector().equalsIgnoreCase("projectarea")) {
        IScmCommandLineArgument projectAreaValue = (IScmCommandLineArgument)properties.get("processarea");
        IProcessArea processArea = null;
        if (projectAreaValue != null) {
          SubcommandUtil.validateArgument(projectAreaValue, RepoUtil.ItemType.PROJECTAREA);
          processArea = RepoUtil.getProcessArea(projectAreaValue, RepoUtil.ItemType.PROJECTAREA, repo, config);
        }
        
        if (IContributor.ITEM_TYPE.equals(ownerType)) {
          if (projectAreaValue == null)
            throw StatusHelper.argSyntax(Messages.WorkspacePropertiesCmd_SPECIFY_PROJECT_IDENTIFIER);
          if (processArea == null) {
            throw StatusHelper.inappropriateArgument(NLS.bind(Messages.ListCmd_NOPROJECTAREA, propertyValue.getItemSelector()));
          }
          readScope.scope = "contributor_deferring_scope";
          readScope.defer_to = processArea.getItemId().getUuidValue();
        } else {
          if (processArea != null) {
            if ((IProjectArea.ITEM_TYPE.equals(ownerType)) && (!processArea.getItemId().getUuidValue().equals(ownerId)))
            {
              throw StatusHelper.inappropriateArgument(Messages.ComponentPropertiesCmd_PROJECTAREA_INVALID_PA_OWNER); }
            if (ITeamArea.ITEM_TYPE.equals(ownerType))
            {
              ITeamArea teamArea = (ITeamArea)RepoUtil.getItem(ITeamArea.ITEM_TYPE, UUID.valueOf(ownerId), repo, 1, config);
              IProjectArea projArea = (IProjectArea)RepoUtil.getItem(IProjectArea.ITEM_TYPE, teamArea.getProjectArea().getItemId(), repo, config);
              if (!projArea.getItemId().equals(processArea.getItemId())) {
                throw StatusHelper.inappropriateArgument(Messages.ComponentPropertiesCmd_PROJECTAREA_INVALID_TA_OWNER);
              }
            }
          }
          readScope.scope = "process_area_scope";
        }
      } else if (propertyValue.getItemSelector().equalsIgnoreCase("accessgroup"))
      {
        IScmCommandLineArgument accessGroupValue = (IScmCommandLineArgument)properties.get("accessgroup");
        if (accessGroupValue == null) {
          throw StatusHelper.argSyntax(Messages.WorkspacePropertiesCmd_SPECIFY_ACCESS_GROUP);
        }
        
        SubcommandUtil.validateArgument(accessGroupValue, RepoUtil.ItemType.ACCESSGROUP);
        IAccessGroup accessGroup = RepoUtil.getAccessGroup(accessGroupValue, repo, config);
        if (accessGroup != null) {
          readScope.scope = "access_group_scope";
          readScope.defer_to = accessGroup.getGroupContextId().getUuidValue();
        } else {
          throw StatusHelper.argSyntax(NLS.bind(Messages.WorkspacePropertiesCmd_INVALID_ACCESS_GROUP, accessGroupValue.getItemSelector()));
        }
      } else if (IContributor.ITEM_TYPE.equals(ownerType))
      {
        IProcessArea processArea = null;
        SubcommandUtil.validateArgument(propertyValue, RepoUtil.ItemType.PROJECTAREA);
        processArea = RepoUtil.getProcessArea(propertyValue, RepoUtil.ItemType.PROJECTAREA, repo, config);
        if (processArea == null) {
          throw StatusHelper.inappropriateArgument(NLS.bind(Messages.ListCmd_NOPROJECTAREA, propertyValue.getItemSelector()));
        }
        
        readScope.scope = "contributor_deferring_scope";
        readScope.defer_to = processArea.getItemId().getUuidValue();
      } else {
        throw StatusHelper.inappropriateArgument(Messages.ComponentPropertiesCmd_CANNOT_SET_VISIBILITY);
      }
    }
    

    try
    {
      client.postPutComponent(parms, null);
    } catch (TeamRepositoryException e) {
      throw StatusHelper.wrap(Messages.ComponentPropertiesCmd_PROPERTY_SET_FAILURE, e, 
        new IndentingPrintStream(config.getContext().stderr()), repo.getRepositoryURI());
    }
  }
  
  private static boolean printResult(JSONArray jCompArray, IClientConfiguration config) {
    IndentingPrintStream err = new IndentingPrintStream(config.getContext().stderr());
    boolean setAllComponentProperties = true;
    
    for (Object obj : jCompArray) {
      JSONObject jComp = (JSONObject)obj;
      

      String comp = (String)jComp.get("component");
      if (jComp.get("uuid") != null) {
        String compItemId = (String)jComp.get("uuid");
        String repoUri = (String)jComp.get("url");
        comp = AliasUtil.selector(comp, UUID.valueOf(compItemId), repoUri, RepoUtil.ItemType.COMPONENT);
      }
      
      long statusCode = ((Long)jComp.get("status-code")).longValue();
      if (statusCode != 0L)
      {
        String errorMsg = (String)jComp.get("error-message");
        err.println(comp);
        err.indent().println(NLS.bind(Messages.Common_ERROR_CODE, Long.valueOf(statusCode)));
        err.indent().println(NLS.bind(Messages.Common_ERROR_MESSAGE, errorMsg));
        setAllComponentProperties = false;
      }
    }
    

    return setAllComponentProperties;
  }
}
