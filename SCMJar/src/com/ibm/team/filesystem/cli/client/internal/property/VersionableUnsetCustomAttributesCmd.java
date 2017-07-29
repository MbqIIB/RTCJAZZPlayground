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
import com.ibm.team.filesystem.client.IRelativeLocation;
import com.ibm.team.filesystem.client.internal.PathLocation;
import com.ibm.team.filesystem.client.rest.IFilesystemRestClient;
import com.ibm.team.filesystem.client.rest.parameters.ParmsResourceProperties;
import com.ibm.team.filesystem.client.rest.parameters.ParmsResourcePropertyChange;
import com.ibm.team.filesystem.client.rest.parameters.ParmsResourcePropertyUpdates;
import com.ibm.team.filesystem.common.internal.rest.client.resource.CustomAttributesDTO;
import com.ibm.team.repository.common.TeamRepositoryException;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IExecutionContext;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IOptionSource;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.ICommandLine;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.OptionKey;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.Options;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.PositionalOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.exceptions.ConflictingOptionException;
import com.ibm.team.rtc.cli.infrastructure.internal.util.IndentingPrintStream;
import java.io.File;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.eclipse.osgi.util.NLS;






public class VersionableUnsetCustomAttributesCmd
  extends AbstractSubcommand
  implements IOptionSource
{
  public VersionableUnsetCustomAttributesCmd() {}
  
  public static final OptionKey OPT_FILES = new OptionKey("files");
  public static final OptionKey OPT_KEY = new OptionKey("key");
  
  public Options getOptions() throws ConflictingOptionException {
    Options options = new Options(false, true);
    SubcommandUtil.addRepoLocationToOptions(options);
    options.addOption(CommonOptions.OPT_DIRECTORY, CommonOptions.OPT_DIRECTORY_ROOT_HELP);
    options.addOption(new PositionalOptionDefinition(OPT_KEY, "key", 1, 1), Messages.ExtendedPropertyRemoveCmd_KeyHelp);
    options.addOption(new PositionalOptionDefinition(OPT_FILES, "files", 1, -1), Messages.ExtendedPropertyRemoveCmd_FilesHelp);
    return options;
  }
  
  public void run() throws FileSystemException {
    ICommandLine cli = config.getSubcommandCommandLine();
    List<String> strPaths = cli.getOptions(OPT_FILES);
    
    List<ILocation> paths = SubcommandUtil.makeAbsolutePaths(config, strPaths);
    String key = cli.getOption(OPT_KEY);
    try
    {
      unsetCustomAttributes(config, key, paths);
    } catch (TeamRepositoryException e) {
      throw StatusHelper.wrap(Messages.ExtendedPropertyRemoveCmd_CouldNotRemoveProperties, e, new IndentingPrintStream(config.getContext().stderr()));
    }
  }
  


  public static void unsetCustomAttributes(IScmClientConfiguration config, String key, List<ILocation> paths)
    throws FileSystemException
  {
    IFilesystemRestClient client = SubcommandUtil.setupDaemon(config);
    

    RepoUtil.loginUrlArgAncOrOnPaths(config, client, paths);
    
    List<ILocation> validPaths = validatePropertyForFiles(client, key, paths);
    ILocation path;
    if (validPaths.size() > 0)
    {
      ParmsResourcePropertyUpdates parms = new ParmsResourcePropertyUpdates();
      resourcePropertyChanges = new ParmsResourcePropertyChange[paths.size()];
      
      for (int i = 0; i < validPaths.size(); i++) {
        path = (ILocation)validPaths.get(i);
        File cfaRootFile = SubcommandUtil.findAncestorCFARoot((File)path.getAdapter(File.class));
        ILocation cfaRoot = new PathLocation(cfaRootFile.getAbsolutePath());
        IRelativeLocation filePath = path.getLocationRelativeTo(cfaRoot);
        
        ParmsResourcePropertyChange change = new ParmsResourcePropertyChange();
        sandboxPath = cfaRoot.toOSString();
        filePath = filePath.toString();
        propertyRemovals = new String[] { key };
        
        resourcePropertyChanges[i] = change;
      }
      try
      {
        client.postExtendedProperties(parms, null);
      } catch (TeamRepositoryException localTeamRepositoryException) {
        throw StatusHelper.propertiesUnavailable(Messages.ExtendedPropertyRemoveCmd_CouldNotRemoveProperties);
      }
    }
    

    paths.removeAll(validPaths);
    if (paths.size() > 0) {
      IndentingPrintStream printStream = new IndentingPrintStream(config.getContext().stderr());
      printStream.println(NLS.bind(Messages.ExtendedPropertyRemoveCmd_UnabletoRemovePropertyForFiles, key));
      for (ILocation path : paths) {
        File cfaRootFile = SubcommandUtil.findAncestorCFARoot((File)path.getAdapter(File.class));
        ILocation cfaRoot = new PathLocation(cfaRootFile.getAbsolutePath());
        IRelativeLocation filePath = path.getLocationRelativeTo(cfaRoot);
        printStream.indent().println(filePath.toString());
      }
    }
    

    if (validPaths.size() > 0) {
      config.getContext().stdout().println(NLS.bind(Messages.ExtendedPropertyRemoveCmd_KeySuccessfullyRemoved, key));
    } else {
      throw StatusHelper.propertiesUnavailable(Messages.ExtendedPropertyRemoveCmd_CouldNotRemoveProperties);
    }
  }
  
  private static List<ILocation> validatePropertyForFiles(IFilesystemRestClient client, String key, List<ILocation> paths) throws FileSystemException {
    Map<String, ILocation> validPropertyPaths = new HashMap();
    
    for (ILocation path : paths) {
      if (!SubcommandUtil.exists(path, null)) {
        throw StatusHelper.disallowed(NLS.bind(Messages.PropertyListCmd_PathDoesNotExist, path.toOSString()));
      }
      
      File cfaRootFile = SubcommandUtil.findAncestorCFARoot((File)path.getAdapter(File.class));
      if (cfaRootFile == null) {
        throw StatusHelper.disallowed(NLS.bind(Messages.PropertySetCmd_CouldNotFindSandboxForPath, path.toOSString()));
      }
      
      validPropertyPaths.put(path.toOSString(), path);
    }
    
    ParmsResourceProperties parms = new ParmsResourceProperties(false, (String[])validPropertyPaths.keySet().toArray(new String[validPropertyPaths.size()]));
    try
    {
      resourcesDTO = client.getExtendedProperties(parms, null);
    } catch (TeamRepositoryException localTeamRepositoryException) { CustomAttributesDTO[] resourcesDTO;
      throw StatusHelper.propertiesUnavailable(Messages.ExtendedPropertyRemoveCmd_CouldNotRemoveProperties);
    }
    CustomAttributesDTO[] resourcesDTO;
    for (int i = 0; i < resourcesDTO.length; i++) {
      CustomAttributesDTO dto = resourcesDTO[i];
      if (!dto.getCustomAttributes().containsKey(key)) {
        validPropertyPaths.remove(dto.getFullPath());
      }
    }
    
    return new ArrayList(validPropertyPaths.values());
  }
}
