package com.ibm.team.filesystem.cli.client.internal.property;

import com.ibm.team.filesystem.cli.client.AbstractSubcommand;
import com.ibm.team.filesystem.cli.client.internal.Messages;
import com.ibm.team.filesystem.cli.core.subcommands.CommonOptions;
import com.ibm.team.filesystem.cli.core.subcommands.IScmClientConfiguration;
import com.ibm.team.filesystem.cli.core.util.JSONPrintUtil;
import com.ibm.team.filesystem.cli.core.util.RepoUtil;
import com.ibm.team.filesystem.cli.core.util.StatusHelper;
import com.ibm.team.filesystem.cli.core.util.SubcommandUtil;
import com.ibm.team.filesystem.client.FileSystemException;
import com.ibm.team.filesystem.client.ILocation;
import com.ibm.team.filesystem.client.internal.PathLocation;
import com.ibm.team.filesystem.client.rest.IFilesystemRestClient;
import com.ibm.team.filesystem.client.rest.parameters.ParmsResourceProperties;
import com.ibm.team.filesystem.common.internal.rest.client.core.ShareDTO;
import com.ibm.team.filesystem.common.internal.rest.client.resource.FilePropertiesDTO;
import com.ibm.team.filesystem.common.internal.rest.client.resource.PermissionsContextDTO;
import com.ibm.team.filesystem.common.internal.rest.client.resource.ResourcePropertiesDTO;
import com.ibm.team.filesystem.common.internal.rest.client.resource.ResourcesDTO;
import com.ibm.team.filesystem.common.internal.rest.client.resource.SymlinkPropertiesDTO;
import com.ibm.team.process.common.IAccessGroup;
import com.ibm.team.process.common.IProjectArea;
import com.ibm.team.process.common.ITeamArea;
import com.ibm.team.repository.client.ITeamRepository;
import com.ibm.team.repository.common.IAuditableHandle;
import com.ibm.team.repository.common.IContributor;
import com.ibm.team.repository.common.TeamRepositoryException;
import com.ibm.team.repository.common.UUID;
import com.ibm.team.repository.common.json.JSONArray;
import com.ibm.team.repository.common.json.JSONObject;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IExecutionContext;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IOptionSource;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.ICommandLine;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.OptionKey;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.Options;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.PositionalOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.exceptions.ConflictingOptionException;
import com.ibm.team.rtc.cli.infrastructure.internal.util.IndentingPrintStream;
import com.ibm.team.rtc.cli.infrastructure.internal.util.StringUtil;
import java.io.File;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Status;
import org.eclipse.osgi.util.NLS;




public class PropertyListCmd
  extends AbstractSubcommand
  implements IOptionSource
{
  private static final int PROPERTY_UNAVAILABLE = 1;
  private static final int FILE_IS_NOT_SHARED = 2;
  private static final int FILE_DOES_NOT_EXIST = 3;
  public static final String PROPERTY_EXECUTABLE = "jazz.executable";
  public static final String PROPERTY_MIME = "jazz.mime";
  public static final String PROPERTY_LINE_DELIMITER = "jazz.line-delimiter";
  public static final String PROPERTY_LINK_TYPE = "jazz.link-type";
  public static final String PROPERTY_ENCODING = "jazz.encoding";
  public static final String PROPERTY_READ_ACCESS = "jazz.read-access";
  public PropertyListCmd() {}
  
  private final class StatusComparator
    implements Comparator<IStatus>
  {
    private StatusComparator() {}
    
    public int compare(IStatus o1, IStatus o2)
    {
      int compare = o1.getSeverity() - o2.getSeverity();
      if (compare == 0) {
        compare = o1.getCode() - o2.getCode();
      }
      if (compare == 0) {
        compare = o1.getMessage().compareTo(o2.getMessage());
      }
      return compare;
    }
  }
  
  private final class MapEntryComparator implements Comparator<Map.Entry<String, ?>> {
    private MapEntryComparator() {}
    
    public int compare(Map.Entry<String, ?> o1, Map.Entry<String, ?> o2) {
      return ((String)o1.getKey()).compareToIgnoreCase((String)o2.getKey());
    }
  }
  
  public static final OptionKey OPT_FILES = new OptionKey("files");
  
  public Options getOptions() throws ConflictingOptionException {
    Options options = new Options(false, true);
    SubcommandUtil.addRepoLocationToOptions(options);
    options.addOption(CommonOptions.OPT_JSON, Messages.Common_JSON_OUTPUT)
      .addOption(CommonOptions.OPT_DIRECTORY, CommonOptions.OPT_DIRECTORY_ROOT_HELP)
      .addOption(CommonOptions.OPT_NO_LOCAL_REFRESH, Messages.Common_DO_NOT_SCAN_FS)
      .addOption(new PositionalOptionDefinition(OPT_FILES, "files", 1, -1), Messages.PropertyListCmd_ListPropertiesHelp);
    return options;
  }
  
  public void run() throws FileSystemException {
    ICommandLine cli = config.getSubcommandCommandLine();
    
    config.setEnableJSON(cli.hasOption(CommonOptions.OPT_JSON));
    
    List<String> strPaths = cli.getOptions(OPT_FILES);
    
    List<ILocation> paths = SubcommandUtil.makeAbsolutePaths(config, strPaths);
    
    MapEntryComparator mapEntryComparator = new MapEntryComparator(null);
    List<Map.Entry<String, Map<String, String>>> properties = new ArrayList();
    List<IStatus> stats = new ArrayList();
    String key = getKey(cli);
    try {
      Map<String, Map<String, String>> map = getProperties(config, key, paths, stats);
      properties.addAll(map.entrySet());
      Collections.sort(properties, mapEntryComparator);
    } catch (TeamRepositoryException e) {
      throw StatusHelper.wrap(Messages.PropertyListCmd_CouldNotFetchProperty, e, new IndentingPrintStream(config.getContext().stderr()));
    }
    
    IndentingPrintStream out = new IndentingPrintStream(config.getContext().stdout());
    JSONArray props = JSONPrintUtil.jsonizeProperties(properties, mapEntryComparator);
    JSONObject jResult = new JSONObject();
    jResult.put("results", props);
    
    if (config.isJSONEnabled()) {
      config.getContext().stdout().print(jResult);
    }
    else {
      printProps(props, out);
    }
    
    if (stats.size() > 0) {
      Collections.sort(stats, new StatusComparator(null));
      for (IStatus status : stats) {
        config.getContext().stderr().println(status.getMessage());
      }
      IStatus mostSevere = (IStatus)stats.get(stats.size() - 1);
      switch (mostSevere.getCode()) {
      case 1: 
        throw StatusHelper.propertiesUnavailable(NLS.bind(Messages.PropertyListCmd_CouldNotGetProperty, key));
      case 2: 
        throw StatusHelper.inappropriateArgument(Messages.PropertyListCmd_FileIsNotShared);
      case 3: 
        throw StatusHelper.disallowed(Messages.PropertyListCmd_FileDoesNotExist);
      }
    }
  }
  
  public String getKey(ICommandLine cli) {
    return null;
  }
  




  public Map<String, Map<String, String>> getProperties(IScmClientConfiguration config, String key, List<ILocation> paths, List<IStatus> stats)
    throws TeamRepositoryException, FileSystemException
  {
    Map<String, ResourcePropertiesDTO> resourceProperties = getResourceProperties(config, paths, stats);
    
    Map<String, Map<String, String>> properties = new HashMap(resourceProperties.size());
    for (String path : resourceProperties.keySet()) {
      HashMap<String, String> map = new HashMap();
      properties.put(path, map);
    }
    
    for (Map.Entry<String, ResourcePropertiesDTO> entry : resourceProperties.entrySet()) {
      String path = (String)entry.getKey();
      ResourcePropertiesDTO resourcePropertiesDTO = (ResourcePropertiesDTO)entry.getValue();
      Map<String, String> map = (Map)properties.get(path);
      

      String readAccess = getReadAccessContext(resourcePropertiesDTO, config);
      if (readAccess.equals(Messages.PropertyListCmd_Inaccessible)) {
        put(map, "jazz.read-access", readAccess, false, path, stats);
      }
      else
      {
        Map<String, String> userProperties = resourcePropertiesDTO.getUserProperties();
        if (key == null) {
          if (resourcePropertiesDTO.getVersionableItemType().equals("file")) {
            put(map, "jazz.encoding", getEncoding(resourcePropertiesDTO), false, path, stats);
            put(map, "jazz.executable", getExecutable(resourcePropertiesDTO), false, path, stats);
            put(map, "jazz.line-delimiter", getLineDelimiter(resourcePropertiesDTO), false, path, stats);
            put(map, "jazz.mime", getMime(resourcePropertiesDTO), false, path, stats);
          } else if (resourcePropertiesDTO.getVersionableItemType().equals("symbolic_link")) {
            put(map, "jazz.link-type", getLinkType(resourcePropertiesDTO), false, path, stats);
          }
          put(map, "jazz.read-access", readAccess, false, path, stats);
          

          if (userProperties != null) {
            for (Map.Entry<String, String> userProperty : userProperties.entrySet()) {
              put(map, (String)userProperty.getKey(), (String)userProperty.getValue(), true, path, stats);
            }
          }
        } else if ("jazz.encoding".equals(key)) {
          boolean warn = !resourcePropertiesDTO.getVersionableItemType().equals("file");
          put(map, "jazz.encoding", getEncoding(resourcePropertiesDTO), warn, path, stats);
        } else if ("jazz.executable".equals(key)) {
          put(map, "jazz.executable", getExecutable(resourcePropertiesDTO), true, path, stats);
        } else if ("jazz.line-delimiter".equals(key)) {
          put(map, key, getLineDelimiter(resourcePropertiesDTO), true, path, stats);
        } else if ("jazz.mime".equals(key)) {
          put(map, key, getMime(resourcePropertiesDTO), true, path, stats);
        } else if ("jazz.link-type".equals(key)) {
          put(map, key, getLinkType(resourcePropertiesDTO), true, path, stats);
        } else if ("jazz.read-access".equals(key)) {
          put(map, "jazz.read-access", getReadAccessContext(resourcePropertiesDTO, config), false, path, stats);
        } else {
          put(map, key, getUserProperty(key, userProperties), true, path, stats);
        }
      }
    }
    return properties;
  }
  
  private void put(Map<String, String> map, String key, String value, boolean logWarning, String path, List<IStatus> stats)
  {
    if (value == null) {
      if (logWarning) {
        stats.add(new Status(2, "com.ibm.team.filesystem.client", 1, NLS.bind(Messages.PropertyListCmd_CouldNotGetPropertyForPath, key, path), null));
      }
    } else {
      map.put(key, value);
    }
  }
  
  private String getLinkType(ResourcePropertiesDTO resourceProperties) {
    SymlinkPropertiesDTO symlinkProperties = resourceProperties.getSymlinkProperties();
    if (symlinkProperties != null) {
      return symlinkProperties.getType();
    }
    return null;
  }
  
  private static String getMime(ResourcePropertiesDTO resourceProperties) {
    FilePropertiesDTO fileProperties = resourceProperties.getFileProperties();
    if (fileProperties != null) {
      return fileProperties.getContentType();
    }
    return null;
  }
  
  private static String getLineDelimiter(ResourcePropertiesDTO resourceProperties) {
    FilePropertiesDTO fileProperties = resourceProperties.getFileProperties();
    if (fileProperties != null) {
      return fileProperties.getLineDelimiter();
    }
    return null;
  }
  
  private static String getExecutable(ResourcePropertiesDTO resourceProperties) {
    FilePropertiesDTO fileProperties = resourceProperties.getFileProperties();
    if (fileProperties != null) {
      return Boolean.toString(fileProperties.isExecutable());
    }
    return null;
  }
  
  private static String getEncoding(ResourcePropertiesDTO resourceProperties) throws FileSystemException {
    FilePropertiesDTO fileProperties = resourceProperties.getFileProperties();
    if (fileProperties != null) {
      return fileProperties.getEncoding();
    }
    return null;
  }
  
  private static String getReadAccessContext(ResourcePropertiesDTO resourceProperties, IScmClientConfiguration config) throws FileSystemException
  {
    String accessContext = Messages.FilePropertiesCmd_COMPONENT_SCOPED;
    
    PermissionsContextDTO context = resourceProperties.getPermissionsContext();
    if (context != null) {
      if (!context.isAccessible()) {
        accessContext = Messages.PropertyListCmd_Inaccessible;
      } else if (context.getReadContext() != null) {
        ITeamRepository repo = RepoUtil.getSharedRepository(config.getRepositoryURI(resourceProperties.getShare().getRepositoryId(), null), false);
        UUID itemId = context.getReadContext().getItemId();
        if (context.getReadContext().getItemType().equals(IContributor.ITEM_TYPE)) {
          IContributor contrib = (IContributor)RepoUtil.getItem(IContributor.ITEM_TYPE, itemId, repo, config);
          accessContext = NLS.bind(Messages.FilePropertiesCmd_CONTRIBUTOR, contrib.getName());
        } else if (context.getReadContext().getItemType().equals(ITeamArea.ITEM_TYPE)) {
          ITeamArea teamArea = (ITeamArea)RepoUtil.getItem(ITeamArea.ITEM_TYPE, itemId, repo, config);
          accessContext = NLS.bind(Messages.FilePropertiesCmd_TEAM_AREA, teamArea.getName());
        } else if (context.getReadContext().getItemType().equals(IProjectArea.ITEM_TYPE)) {
          IProjectArea projArea = (IProjectArea)RepoUtil.getItem(IProjectArea.ITEM_TYPE, itemId, repo, config);
          accessContext = NLS.bind(Messages.FilePropertiesCmd_PROJECT_AREA, projArea.getName());
        } else if (context.getReadContext().getItemType().equals(IAccessGroup.ITEM_TYPE)) {
          IAccessGroup accessGroup = RepoUtil.getAccessGroup(itemId, repo, config);
          accessContext = NLS.bind(Messages.FilePropertiesCmd_ACCESS_GROUP, accessGroup.getName());
        } else {
          accessContext = Messages.Common_UNKNOWN;
        }
      }
    }
    
    return accessContext;
  }
  
  private static String getUserProperty(String key, Map<String, String> userProperties) {
    String value = null;
    
    if (userProperties != null) {
      for (Map.Entry<String, String> userProperty : userProperties.entrySet()) {
        if (key.equals(userProperty.getKey())) {
          value = (String)userProperty.getValue();
          break;
        }
      }
    }
    
    return value;
  }
  

  private Map<String, ResourcePropertiesDTO> getResourceProperties(IScmClientConfiguration config, List<ILocation> paths, List<IStatus> stats)
    throws TeamRepositoryException, FileSystemException
  {
    List<ILocation> validPaths = new ArrayList();
    for (int i = 0; i < paths.size(); i++) {
      ILocation path = (ILocation)paths.get(i);
      
      String osString = path.toOSString();
      if (!SubcommandUtil.exists(path, null)) {
        stats.add(new Status(4, "com.ibm.team.filesystem.client", 3, 
          NLS.bind(Messages.PropertyListCmd_PathDoesNotExist, osString), null));
      } else {
        File cfaRootFile = SubcommandUtil.findAncestorCFARoot(path.toOSString());
        if (cfaRootFile == null) {
          stats.add(new Status(4, "com.ibm.team.filesystem.client", 2, 
            NLS.bind(Messages.PropertyListCmd_PathIsNotShared, osString), null));
        } else {
          validPaths.add(path);
        }
      }
    }
    
    List<String> sboxes = new ArrayList();
    
    List<String> validPathStrings = new ArrayList();
    Map<ILocation, List<ILocation>> sandbox2FileMap = new HashMap(validPaths.size());
    List<ILocation> pathList; for (ILocation path : validPaths) {
      ILocation cfaRoot = new PathLocation(new Path(SubcommandUtil.findAncestorCFARoot(path.toOSString()).getAbsolutePath()));
      
      if (!sandbox2FileMap.containsKey(cfaRoot)) {
        pathList = new ArrayList();
        pathList.add(path);
        sandbox2FileMap.put(cfaRoot, pathList);
        sboxes.add(cfaRoot.toOSString());
      } else {
        ((List)sandbox2FileMap.get(cfaRoot)).add(path);
      }
      
      validPathStrings.add(path.toOSString());
    }
    
    IFilesystemRestClient client = SubcommandUtil.startDaemon(config.getSandboxes(), config);
    

    RepoUtil.loginUrlArgAncOrOnPaths(config, client, validPaths);
    
    String[] arr = (String[])sboxes.toArray(new String[sboxes.size()]);
    SubcommandUtil.registerSandboxes(arr, client, config);
    

    if (SubcommandUtil.shouldRefreshFileSystem(config)) {
      for (ILocation sandbox : sandbox2FileMap.keySet()) {
        SubcommandUtil.refreshPaths(sandbox, (List)sandbox2FileMap.get(sandbox), client, config);
      }
    }
    
    ParmsResourceProperties parms = new ParmsResourceProperties(true, (String[])validPathStrings.toArray(new String[validPathStrings.size()]));
    ResourcesDTO resourcesDTO = client.getResourceProperties(parms, null);
    Map<String, ResourcePropertiesDTO> resourceProperties = new HashMap(validPaths.size());
    
    List<ResourcePropertiesDTO> resourceProperties2 = resourcesDTO.getResourceProperties();
    for (ResourcePropertiesDTO dto : resourceProperties2) {
      if (dto.getShare() == null) {
        stats.add(new Status(4, "com.ibm.team.filesystem.client", 2, 
          NLS.bind(Messages.PropertyListCmd_PathIsNotShared, dto.getFullPath()), null));
      } else if (dto.getItemId() == null) {
        stats.add(new Status(4, "com.ibm.team.filesystem.client", 3, 
          NLS.bind(Messages.PropertyListCmd_PathDoesNotExist, dto.getFullPath()), null));
      }
      else {
        String fullPath = dto.getFullPath();
        resourceProperties.put(fullPath, dto);
      }
    }
    
    return resourceProperties;
  }
  
  private void printProps(JSONArray fileProps, IndentingPrintStream out)
  {
    if (fileProps == null) {
      return;
    }
    
    for (Object obj : fileProps)
    {
      JSONObject fileProp = (JSONObject)obj;
      
      out.println((String)fileProp.get("name"));
      
      JSONArray props = (JSONArray)fileProp.get("properties");
      
      if (props != null)
      {


        int maxWidth = 0;
        JSONObject prop;
        for (Object entry : props) {
          prop = (JSONObject)entry;
          String property = (String)prop.get("property");
          
          maxWidth = Math.max(maxWidth, property.length());
        }
        
        IndentingPrintStream indent = out.indent();
        
        for (Object entry : props)
        {
          JSONObject prop = (JSONObject)entry;
          indent.println(NLS.bind(Messages.PropertyListCmd_KeyValue, 
            StringUtil.pad((String)prop.get("property"), maxWidth), 
            prop.get("value")));
        }
      }
    }
  }
}
