package com.ibm.team.filesystem.cli.client.internal.property;

import com.ibm.team.filesystem.cli.client.AbstractSubcommand;
import com.ibm.team.filesystem.cli.client.internal.Messages;
import com.ibm.team.filesystem.cli.core.subcommands.CommonOptions;
import com.ibm.team.filesystem.cli.core.subcommands.IScmClientConfiguration;
import com.ibm.team.filesystem.cli.core.util.RepoUtil;
import com.ibm.team.filesystem.cli.core.util.StatusHelper;
import com.ibm.team.filesystem.cli.core.util.SubcommandUtil;
import com.ibm.team.filesystem.client.FileSystemException;
import com.ibm.team.filesystem.client.ILocation;
import com.ibm.team.filesystem.client.internal.PathLocation;
import com.ibm.team.filesystem.client.rest.IFilesystemRestClient;
import com.ibm.team.filesystem.client.rest.parameters.ParmsResourceProperties;
import com.ibm.team.filesystem.common.internal.rest.client.resource.CustomAttributesDTO;
import com.ibm.team.repository.common.TeamRepositoryException;
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






public class VersionableShowCustomAttributesCmd
  extends AbstractSubcommand
  implements IOptionSource
{
  private static final int PROPERTY_UNAVAILABLE = 1;
  private static final int FILE_IS_NOT_SHARED = 2;
  private static final int FILE_DOES_NOT_EXIST = 3;
  public VersionableShowCustomAttributesCmd() {}
  
  private static final class StatusComparator
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
  
  private static final class MapEntryComparator implements Comparator<Map.Entry<String, ?>> {
    private MapEntryComparator() {}
    
    public int compare(Map.Entry<String, ?> o1, Map.Entry<String, ?> o2) {
      return ((String)o1.getKey()).compareToIgnoreCase((String)o2.getKey());
    }
  }
  
  public String getKey(ICommandLine cli) {
    return null;
  }
  
  public static final OptionKey OPT_FILES = new OptionKey("files");
  
  public Options getOptions() throws ConflictingOptionException {
    Options options = new Options(false, true);
    SubcommandUtil.addRepoLocationToOptions(options);
    options.addOption(CommonOptions.OPT_JSON, Messages.Common_JSON_OUTPUT)
      .addOption(CommonOptions.OPT_DIRECTORY, CommonOptions.OPT_DIRECTORY_ROOT_HELP)
      .addOption(new PositionalOptionDefinition(OPT_FILES, "files", 1, -1), Messages.ExtendedPropertyListCmd_ListPropertiesHelp);
    return options;
  }
  
  public void run() throws FileSystemException {
    ICommandLine cli = config.getSubcommandCommandLine();
    
    config.setEnableJSON(cli.hasOption(CommonOptions.OPT_JSON));
    
    List<String> strPaths = cli.getOptions(OPT_FILES);
    
    List<ILocation> paths = SubcommandUtil.makeAbsolutePaths(config, strPaths);
    
    String key = cli.getOption(VersionableSetCustomAttributesCmd.OPT_KEY);
    
    showCustomAttributes(paths, key, config);
  }
  
  public static void showCustomAttributes(List<ILocation> paths, String key, IScmClientConfiguration config) throws FileSystemException {
    MapEntryComparator mapEntryComparator = new MapEntryComparator(null);
    List<Map.Entry<String, Map<String, String>>> properties = new ArrayList();
    List<IStatus> stats = new ArrayList();
    try {
      Map<String, Map<String, String>> map = getProperties(config, key, paths, stats);
      properties.addAll(map.entrySet());
      Collections.sort(properties, mapEntryComparator);
    } catch (TeamRepositoryException e) {
      throw StatusHelper.wrap(Messages.ExtendedPropertyListCmd_CouldNotFetchProperty, e, new IndentingPrintStream(config.getContext().stderr()));
    }
    
    IndentingPrintStream out = new IndentingPrintStream(config.getContext().stdout());
    JSONArray props = jsonize(properties, mapEntryComparator);
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
  



  public static Map<String, Map<String, String>> getProperties(IScmClientConfiguration config, String key, List<ILocation> paths, List<IStatus> stats)
    throws TeamRepositoryException, FileSystemException
  {
    Map<String, CustomAttributesDTO> resourceProperties = getExtenedProperties(config, paths, stats);
    
    Map<String, Map<String, String>> properties = new HashMap(resourceProperties.size());
    for (String path : resourceProperties.keySet()) {
      HashMap<String, String> map = new HashMap();
      properties.put(path, map);
    }
    
    for (Map.Entry<String, CustomAttributesDTO> entry : resourceProperties.entrySet()) {
      String path = (String)entry.getKey();
      CustomAttributesDTO resourcePropertiesDTO = (CustomAttributesDTO)entry.getValue();
      Map<String, String> map = (Map)properties.get(path);
      
      Map<String, String> extendedProperties = resourcePropertiesDTO.getCustomAttributes();
      if (key == null)
      {
        if (extendedProperties != null) {
          for (Map.Entry<String, String> userProperty : extendedProperties.entrySet()) {
            put(map, (String)userProperty.getKey(), (String)userProperty.getValue(), true, path, stats);
          }
        }
      } else {
        put(map, key, getExtendedProperty(key, extendedProperties), true, path, stats);
      }
    }
    
    return properties;
  }
  
  private static void put(Map<String, String> map, String key, String value, boolean logWarning, String path, List<IStatus> stats)
  {
    if (value == null) {
      if (logWarning) {
        stats.add(new Status(2, "com.ibm.team.filesystem.client", 1, NLS.bind(Messages.ExtendedPropertyListCmd_CouldNotGetPropertyForPath, key, path), null));
      }
    } else {
      map.put(key, value);
    }
  }
  
  private static String getExtendedProperty(String key, Map<String, String> extendedProperties)
  {
    String value = null;
    
    if (extendedProperties != null) {
      for (Map.Entry<String, String> extendedProperty : extendedProperties.entrySet()) {
        if (key.equals(extendedProperty.getKey())) {
          value = (String)extendedProperty.getValue();
          break;
        }
      }
    }
    
    return value;
  }
  

  private static Map<String, CustomAttributesDTO> getExtenedProperties(IScmClientConfiguration config, List<ILocation> paths, List<IStatus> stats)
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
    CustomAttributesDTO[] resourcesDTO = client.getExtendedProperties(parms, null);
    Map<String, CustomAttributesDTO> extendedProperties = new HashMap(validPaths.size());
    
    for (int i = 0; i < resourcesDTO.length; i++) {
      CustomAttributesDTO dto = resourcesDTO[i];
      if (dto == null) {
        stats.add(new Status(4, "com.ibm.team.filesystem.client", 3, 
          Messages.PropertyListCmd_FileDoesNotExist, null));
      }
      else if (dto.getItemId() == null) {
        stats.add(new Status(4, "com.ibm.team.filesystem.client", 3, 
          NLS.bind(Messages.PropertyListCmd_PathDoesNotExist, dto.getFullPath()), null));
      }
      else {
        String fullPath = dto.getFullPath();
        extendedProperties.put(fullPath, dto);
      }
    }
    
    return extendedProperties;
  }
  
  private static JSONArray jsonize(List<Map.Entry<String, Map<String, String>>> properties, MapEntryComparator mapEntryComparator)
  {
    JSONArray fileProps = new JSONArray();
    
    for (int i = 0; i < properties.size(); i++) {
      Map.Entry<String, Map<String, String>> fileProperties = (Map.Entry)properties.get(i);
      
      JSONObject filePath = new JSONObject();
      filePath.put("name", fileProperties.getKey());
      
      List<Map.Entry<String, String>> map = new ArrayList(((Map)fileProperties.getValue()).entrySet());
      Collections.sort(map, mapEntryComparator);
      
      JSONArray props = new JSONArray();
      for (Map.Entry<String, String> entry : map) {
        JSONObject prop = new JSONObject();
        prop.put("property", entry.getKey());
        prop.put("value", entry.getValue());
        props.add(prop);
      }
      
      if (props.size() > 0) {
        filePath.put("properties", props);
      }
      
      fileProps.add(filePath);
    }
    
    return fileProps;
  }
  
  private static void printProps(JSONArray fileProps, IndentingPrintStream out)
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
            (String)prop.get("value")));
        }
      }
    }
  }
}
