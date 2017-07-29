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
import com.ibm.team.process.common.IAccessGroup;
import com.ibm.team.process.common.IProjectArea;
import com.ibm.team.process.common.IProjectAreaHandle;
import com.ibm.team.process.common.ITeamArea;
import com.ibm.team.repository.client.ITeamRepository;
import com.ibm.team.repository.common.IItemType;
import com.ibm.team.repository.common.TeamRepositoryException;
import com.ibm.team.repository.common.UUID;
import com.ibm.team.repository.common.json.JSONArray;
import com.ibm.team.repository.common.json.JSONObject;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IClientConfiguration;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IExecutionContext;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IOptionSource;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.ICommandLine;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.Options;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.PositionalOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.exceptions.ConflictingOptionException;
import com.ibm.team.rtc.cli.infrastructure.internal.util.IndentingPrintStream;
import com.ibm.team.scm.common.IComponent;
import com.ibm.team.scm.common.internal.rich.rest.dto.ScmComponent2;
import com.ibm.team.scm.common.internal.rich.rest.dto.ScmHandle;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.osgi.util.NLS;






public class ComponentPropertyListCmd
  extends AbstractSubcommand
  implements IOptionSource
{
  IFilesystemRestClient client;
  public static final String NAME_PROPERTY = "name";
  public static final String OWNEDBY_PROPERTY = "ownedby";
  public static final String OWNEDBY_ALIAS_PROPERTY = "owned";
  public static final String VISIBILITY_PROPERTY = "visibility";
  public static final String VISIBILITY_ALIAS_PROPERTY = "visi";
  public static final String TEAMAREA_VISIBILITY = "teamarea";
  public static final String PROJECTAREA_VISIBILITY = "projectarea";
  public static final String ACCESSGROUP_VISIBILITY = "accessgroup";
  public static final String PUBLIC_VISIBILITY = "public";
  public static final String PRIVATE_VISIBILITY = "private";
  public static final String UNKNOWN = "unknown";
  public static final String PROCESS_AREA_PROPERTY = "processarea";
  
  public ComponentPropertyListCmd() {}
  
  public static final String[] PROPERTIES = { "name", "ownedby", "owned", "visibility", "visi" };
  
  public Options getOptions() throws ConflictingOptionException {
    Options options = new Options(false);
    
    SubcommandUtil.addRepoLocationToOptions(options);
    options.addOption(new PositionalOptionDefinition(CommonOptions.OPT_COMPONENTS_SELECTOR, "component", 1, -1, "@"), 
      Messages.ComponentPropertiesCmdOptions_COMPONENT_LIST_HELP)
      .addOption(CommonOptions.OPT_JSON, Messages.Common_JSON_OUTPUT);
    
    return options;
  }
  
  public void run() throws FileSystemException
  {
    listProperties(null);
  }
  
  protected void listProperties(String propertyName) throws FileSystemException {
    ICommandLine cli = config.getSubcommandCommandLine();
    config.setEnableJSON(cli.hasOption(CommonOptions.OPT_JSON));
    
    if ((propertyName != null) && (!Arrays.asList(PROPERTIES).contains(propertyName))) {
      throw StatusHelper.argSyntax(NLS.bind(Messages.WorkspacePropertiesCmd_INVALID_PROPERTY_NAME, propertyName));
    }
    
    List<IScmCommandLineArgument> compSelectorList = ScmCommandLineArgument.createList(cli.getOptionValues(CommonOptions.OPT_COMPONENTS_SELECTOR), config);
    SubcommandUtil.validateArgument(compSelectorList, RepoUtil.ItemType.COMPONENT);
    
    List<String> propertyNames = null;
    if (propertyName != null) {
      propertyNames = new ArrayList(1);
      propertyNames.add(propertyName.toLowerCase());
    }
    
    client = SubcommandUtil.setupDaemon(config);
    
    listProperties(compSelectorList, propertyName, propertyNames, client, config);
  }
  

  public static void listProperties(List<IScmCommandLineArgument> compSelectorList, String propertyName, List<String> propertyNames, IFilesystemRestClient client, IScmClientConfiguration config)
    throws FileSystemException
  {
    JSONArray jCompArray = new JSONArray();
    
    for (IScmCommandLineArgument compSelector : compSelectorList) {
      JSONObject jComp = listProperties(compSelector, propertyNames, client, config);
      jCompArray.add(jComp);
    }
    
    boolean retrievedComponentProperties;
    boolean retrievedComponentProperties;
    if (config.isJSONEnabled()) {
      if (!jCompArray.isEmpty()) {
        JSONObject jResult = new JSONObject();
        jResult.put("components", jCompArray);
        config.getContext().stdout().print(jResult);
      }
      retrievedComponentProperties = hasAllComponentProperties(jCompArray);
    } else {
      retrievedComponentProperties = printProperties(jCompArray, propertyName == null, config);
    }
    
    if (!retrievedComponentProperties) {
      throw StatusHelper.propertiesUnavailable(Messages.ComponentPropertiesCmd_PROPERTY_LIST_FAILURE);
    }
  }
  
  private static JSONObject listProperties(IScmCommandLineArgument compSelector, List<String> propertyNames, IFilesystemRestClient client, IScmClientConfiguration config) throws FileSystemException
  {
    JSONObject jComp = new JSONObject();
    long statusCode = 0L;
    String errorMsg = null;
    ITeamRepository repo = null;
    IComponent comp = null;
    
    try
    {
      repo = RepoUtil.loginUrlArgAncestor(config, client, compSelector);
      
      comp = RepoUtil.getComponent(compSelector.getItemSelector(), repo, config);
      
      JSONObject jProps = getProperties(comp, propertyNames, repo, client, config);
      jComp.put("component", comp.getName());
      jComp.put("properties", jProps);
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
      jComp.put("error-message", errorMsg);
    }
    jComp.put("status-code", Long.valueOf(statusCode));
    
    return jComp;
  }
  
  private static JSONObject getProperties(IComponent comp, List<String> propertyNames, ITeamRepository repo, IFilesystemRestClient client2, IScmClientConfiguration config)
    throws FileSystemException
  {
    JSONObject jProps = new JSONObject();
    new IndentingPrintStream(config.getContext().stdout());
    

    ScmComponent2 compInfo = RepoUtil.getComponentById(comp.getItemId().getUuidValue(), repo, config);
    

    jProps.put("name", comp.getName());
    
    jProps.put("uuid", comp.getItemId().getUuidValue());
    
    jProps.put("url", repo.getRepositoryURI());
    

    String ownerName = RepoUtil.getOwnerName(compInfo.getOwner().getItemId(), compInfo.getOwner().getItemType(), repo, config);
    if ((propertyNames == null) || (propertyNames.contains("ownedby")) || (propertyNames.contains("owned"))) {
      IItemType itemType = SubcommandUtil.getOwnerType(compInfo.getOwner().getItemType());
      JSONObject jOwnedBy = new JSONObject();
      jOwnedBy.put("name", ownerName);
      jOwnedBy.put("uuid", compInfo.getOwner().getItemId());
      jOwnedBy.put("url", repo.getRepositoryURI());
      jOwnedBy.put("type", SubcommandUtil.getItemType(itemType).toString());
      jProps.put("ownedby", jOwnedBy);
    }
    

    if ((propertyNames == null) || (propertyNames.contains("visibility")) || (propertyNames.contains("visi"))) {
      JSONObject jVisibility = new JSONObject();
      JSONObject jInfo = null;
      
      if (compInfo.getReadScope().equalsIgnoreCase("public_scope")) {
        jVisibility.put("type", "public");
      } else if (compInfo.getReadScope().equalsIgnoreCase("private_scope")) {
        jVisibility.put("type", "private");
      } else if (compInfo.getReadScope().equalsIgnoreCase("contributor_deferring_scope")) {
        jVisibility.put("type", "projectarea");
        String defersTo = RepoUtil.getOwnerName(compInfo.getReadDefersTo().getItemId(), compInfo.getReadDefersTo().getItemType(), repo, config);
        
        jInfo = new JSONObject();
        jInfo.put("name", defersTo);
        jInfo.put("uuid", compInfo.getReadDefersTo().getItemId());
        jInfo.put("url", repo.getRepositoryURI());
        jInfo.put("type", SubcommandUtil.getItemType(SubcommandUtil.getOwnerType(compInfo.getReadDefersTo().getItemType())).toString());
        jInfo.put("contributor-deferring", Boolean.TRUE);
      } else if (compInfo.getReadScope().equalsIgnoreCase("process_area_scope")) {
        jVisibility.put("type", "projectarea");
        IProjectArea projArea = null;
        

        IItemType ownerType = SubcommandUtil.getOwnerType(compInfo.getOwner().getItemType());
        if (ITeamArea.ITEM_TYPE.equals(ownerType)) {
          ITeamArea teamArea = (ITeamArea)RepoUtil.getItem(ITeamArea.ITEM_TYPE, UUID.valueOf(compInfo.getOwner().getItemId()), repo, 1, config);
          projArea = (IProjectArea)RepoUtil.getItem(IProjectArea.ITEM_TYPE, teamArea.getProjectArea().getItemId(), repo, config);
        } else if (IProjectArea.ITEM_TYPE.equals(ownerType)) {
          projArea = (IProjectArea)RepoUtil.getItem(IProjectArea.ITEM_TYPE, UUID.valueOf(compInfo.getOwner().getItemId()), repo, 1, config);
        }
        
        if (projArea != null) {
          jInfo = new JSONObject();
          jInfo.put("name", projArea.getName());
          jInfo.put("uuid", projArea.getItemId().getUuidValue());
          jInfo.put("url", repo.getRepositoryURI());
          jInfo.put("type", RepoUtil.ItemType.PROJECTAREA.toString());
        }
      } else if (compInfo.getReadScope().equalsIgnoreCase("team_area_private_scope")) {
        jVisibility.put("type", "teamarea");
        jInfo = new JSONObject();
        jInfo.put("name", ownerName);
        jInfo.put("uuid", compInfo.getOwner().getItemId());
        jInfo.put("url", repo.getRepositoryURI());
        jInfo.put("type", RepoUtil.ItemType.TEAMAREA.toString());
      } else if (compInfo.getReadScope().equalsIgnoreCase("access_group_scope")) {
        jVisibility.put("type", "accessgroup");
        ScmHandle accessGroupId = compInfo.getReadDefersTo();
        IAccessGroup accessGroup = accessGroupId != null ? RepoUtil.getAccessGroup(UUID.valueOf(accessGroupId.getItemId()), repo, config) : null;
        if (accessGroup != null) {
          jInfo = new JSONObject();
          jInfo.put("name", accessGroup.getName());
          jInfo.put("uuid", accessGroup.getGroupContextId().getUuidValue());
          jInfo.put("url", repo.getRepositoryURI());
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
    
    return jProps;
  }
  
  private static boolean printProperties(JSONArray jCompArray, boolean printCaption, IClientConfiguration config) throws FileSystemException {
    IndentingPrintStream out = new IndentingPrintStream(config.getContext().stdout());
    IndentingPrintStream err = new IndentingPrintStream(config.getContext().stderr());
    boolean retrievedAllComponentProperties = true;
    
    for (Object obj : jCompArray) {
      JSONObject jComp = (JSONObject)obj;
      boolean result = printProperties(jComp, printCaption, out, err);
      if (!result) {
        retrievedAllComponentProperties = false;
      }
    }
    
    return retrievedAllComponentProperties;
  }
  

  public static boolean printProperties(JSONObject jComp, boolean printCaption, IndentingPrintStream out, IndentingPrintStream err)
  {
    String comp = (String)jComp.get("component");
    
    long statusCode = ((Long)jComp.get("status-code")).longValue();
    if (statusCode != 0L)
    {
      String errorMsg = (String)jComp.get("error-message");
      err.println(comp);
      err.indent().println(NLS.bind(Messages.Common_ERROR_CODE, Long.valueOf(statusCode)));
      err.indent().println(NLS.bind(Messages.Common_ERROR_MESSAGE, errorMsg));
      return false;
    }
    

    JSONObject jProps = (JSONObject)jComp.get("properties");
    
    String name = (String)jProps.get("name");
    String itemId = (String)jProps.get("uuid");
    String repoUri = (String)jProps.get("url");
    

    String compTitle = AliasUtil.selector(name, UUID.valueOf(itemId), repoUri, RepoUtil.ItemType.COMPONENT);
    out.println(compTitle);
    

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
      } else if ((visibilityType.equals("projectarea")) || (visibilityType.equals("teamarea"))) {
        JSONObject jInfo = (JSONObject)jVisibility.get("info");
        if (jInfo != null) {
          String infoName = (String)jInfo.get("name");
          String infoUuid = (String)jInfo.get("uuid");
          String infoUri = (String)jInfo.get("url");
          RepoUtil.ItemType infoType = RepoUtil.ItemType.valueOf((String)jInfo.get("type"));
          
          visibilityInfo = AliasUtil.selector(infoName, UUID.valueOf(infoUuid), infoUri, infoType);
          if (visibilityType.equals("projectarea")) {
            visibilityInfo = NLS.bind(Messages.WorkspacePropertiesCmd_PROJECT_SCOPED, visibilityInfo);
          } else {
            visibilityInfo = NLS.bind(Messages.WorkspacePropertiesCmd_TEAM_PRIVATE, visibilityInfo);
          }
        }
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
          visibilityInfo = NLS.bind(Messages.WorkspacePropertiesCmd_ACCESS_GROUP, Messages.Common_UNKNOWN);
        }
      }
      out.indent().println(printCaption ? NLS.bind(Messages.WorkspacePropertiesCmd_VISIBILITY, visibilityInfo) : visibilityInfo);
    }
    
    return true;
  }
  
  public static boolean hasAllComponentProperties(JSONArray jCompArray) {
    for (Object obj : jCompArray) {
      JSONObject jComp = (JSONObject)obj;
      
      Long statusCode = (Long)jComp.get("status-code");
      if (statusCode.longValue() != 0L) {
        return false;
      }
    }
    
    return true;
  }
}
