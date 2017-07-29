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
import com.ibm.team.filesystem.client.ResourceType;
import com.ibm.team.filesystem.client.internal.PathLocation;
import com.ibm.team.filesystem.client.internal.RelativeLocation;
import com.ibm.team.filesystem.client.rest.IFilesystemRestClient;
import com.ibm.team.filesystem.client.rest.parameters.ParmsInvalidMimeTypeDilemmaHandler;
import com.ibm.team.filesystem.client.rest.parameters.ParmsInvalidPropertiesDilemmaHandler;
import com.ibm.team.filesystem.client.rest.parameters.ParmsLineDelimiterDilemmaHandler;
import com.ibm.team.filesystem.client.rest.parameters.ParmsLineDelimiterErrorInstructions;
import com.ibm.team.filesystem.client.rest.parameters.ParmsProperty;
import com.ibm.team.filesystem.client.rest.parameters.ParmsResourcePropertyChange;
import com.ibm.team.filesystem.client.rest.parameters.ParmsResourcePropertyUpdates;
import com.ibm.team.filesystem.common.internal.rest.client.core.PathDTO;
import com.ibm.team.filesystem.common.internal.rest.client.core.ShareableDTO;
import com.ibm.team.filesystem.common.internal.rest.client.resource.InvalidPropertyDTO;
import com.ibm.team.filesystem.common.internal.rest.client.resource.LineDelimiterErrorDTO;
import com.ibm.team.filesystem.common.internal.rest.client.resource.MimeTypeErrorDTO;
import com.ibm.team.filesystem.common.internal.rest.client.resource.PropertyFailureDTO;
import com.ibm.team.filesystem.common.internal.rest.client.resource.ResourcePropertiesDTO;
import com.ibm.team.filesystem.common.internal.rest.client.resource.ResourcePropertyChangeResultDTO;
import com.ibm.team.repository.common.TeamRepositoryException;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IExecutionContext;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IOptionSource;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.ICommandLine;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.NamedOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.OptionKey;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.Options;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.PositionalOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.exceptions.ConflictingOptionException;
import com.ibm.team.rtc.cli.infrastructure.internal.util.IndentingPrintStream;
import com.ibm.team.rtc.cli.infrastructure.internal.util.StringUtil;
import java.io.File;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.eclipse.osgi.util.NLS;









public class PropertySetCmd
  extends AbstractSubcommand
  implements IOptionSource
{
  public static final OptionKey OPT_FILES = new OptionKey("files");
  public static final OptionKey OPT_KEY = new OptionKey("key");
  public static final OptionKey OPT_VALUE = new OptionKey("value");
  public static final NamedOptionDefinition OPT_FORCE = new NamedOptionDefinition("f", "force", 0);
  private IFilesystemRestClient client;
  
  public PropertySetCmd() {}
  
  public Options getOptions() throws ConflictingOptionException { Options options = new Options(false, true);
    SubcommandUtil.addRepoLocationToOptions(options);
    options.addOption(CommonOptions.OPT_DIRECTORY, CommonOptions.OPT_DIRECTORY_ROOT_HELP);
    options.addOption(CommonOptions.OPT_NO_LOCAL_REFRESH, Messages.Common_DO_NOT_SCAN_FS);
    options.addOption(OPT_FORCE, Messages.PropertySetCmd_OPT_FORCE_HELP);
    options.addOption(new PositionalOptionDefinition(OPT_KEY, "key", 1, 1), Messages.PropertySetCmd_KeyHelp);
    options.addOption(new PositionalOptionDefinition(OPT_VALUE, "value", 1, 1), Messages.PropertySetCmd_ValueHelp);
    options.addOption(new PositionalOptionDefinition(OPT_FILES, "files", 1, -1), Messages.PropertySetCmd_FilesHelp);
    return options;
  }
  
  public void run() throws FileSystemException {
    ICommandLine cli = config.getSubcommandCommandLine();
    List<String> strPaths = cli.getOptions(OPT_FILES);
    
    List<ILocation> paths = SubcommandUtil.makeAbsolutePaths(config, strPaths);
    
    String key = cli.getOption(OPT_KEY);
    String value = cli.getOption(OPT_VALUE);
    boolean force = cli.hasOption(OPT_FORCE);
    
    client = SubcommandUtil.setupDaemon(config);
    
    for (ILocation path : paths) {
      if (!SubcommandUtil.exists(path, null)) {
        throw StatusHelper.disallowed(NLS.bind(Messages.PropertyListCmd_PathDoesNotExist, 
          path.toOSString()));
      }
    }
    

    List<ResourcePropertiesDTO> resPropList = RepoUtil.getResourceProperties(strPaths, 
      SubcommandUtil.shouldRefreshFileSystem(config), client, config, false);
    for (ResourcePropertiesDTO resProp : resPropList) {
      if (resProp.getItemId() == null) {
        throw StatusHelper.disallowed(NLS.bind(Messages.PropertyListCmd_PathDoesNotExist, 
          resProp.getFullPath()));
      }
    }
    try
    {
      setProperty(config, key, value, paths, force);
    } catch (TeamRepositoryException e) {
      throw StatusHelper.wrap(Messages.PropertySetCmd_CouldNotSetProperties, e, 
        new IndentingPrintStream(config.getContext().stderr()));
    }
  }
  




  public void setProperty(IScmClientConfiguration config, String key, String value, List<ILocation> paths, boolean force)
    throws TeamRepositoryException, FileSystemException
  {
    String normalizedValue = value;
    if (key.equals("jazz.executable")) {
      normalizedValue = value.toLowerCase(Locale.ENGLISH);
      if ((!"true".equals(normalizedValue)) && (!"false".equals(normalizedValue))) {
        throw StatusHelper.inappropriateArgument(NLS.bind(Messages.PropertySetCmd_ExecutableValue, value));
      }
    } else if (key.equals("jazz.line-delimiter")) {
      if (value.equalsIgnoreCase("platform")) {
        normalizedValue = "platform";
      } else if (value.equalsIgnoreCase("cr")) {
        normalizedValue = "cr";
      } else if (value.equalsIgnoreCase("lf")) {
        normalizedValue = "lf";
      } else if (value.equalsIgnoreCase("crlf")) {
        normalizedValue = "crlf";
      } else if (value.equalsIgnoreCase("none")) {
        normalizedValue = "none";
      } else {
        throw StatusHelper.inappropriateArgument(NLS.bind(Messages.PropertySetCmd_InvalidLineDelimiter, value));
      }
    } else {
      if (key.equals("jazz.encoding"))
        throw StatusHelper.inappropriateArgument(Messages.PropertySetCmd_SetEncoding);
      if (key.equals("jazz.link-type")) {
        if (value.equalsIgnoreCase("file")) {
          normalizedValue = "file";
        } else if (value.equalsIgnoreCase("directory")) {
          normalizedValue = "directory";
        } else {
          throw StatusHelper.inappropriateArgument(NLS.bind(Messages.PropertySetCmd_InvalidLinkType, value));
        }
      } else if (key.equals("jazz.read-access")) {
        throw StatusHelper.inappropriateArgument(NLS.bind(Messages.PropertySetCmd_Cannot_Set_AccessContext, 
          "jazz.read-access", config.getContext().getAppName()));
      }
    }
    
    RepoUtil.loginUrlArgAncOrOnPaths(config, client, paths);
    
    ParmsResourcePropertyUpdates parms = new ParmsResourcePropertyUpdates();
    lineDelimiterDilemmaHandler = new ParmsLineDelimiterDilemmaHandler();
    lineDelimiterDilemmaHandler.generalLineDelimiterErrorInstruction = "cancel";
    if (force) {
      lineDelimiterDilemmaHandler.generalLineDelimiterErrorInstruction = "continue";
      lineDelimiterDilemmaHandler.lineDelimiterErrorInstructions = new ParmsLineDelimiterErrorInstructions[paths.size()];
    }
    
    invalidPropertiesDilemmaHandler = new ParmsInvalidPropertiesDilemmaHandler();
    invalidPropertiesDilemmaHandler.generalInstruction = "cancel";
    
    invalidMimeTypeDilemmaHandler = new ParmsInvalidMimeTypeDilemmaHandler();
    invalidMimeTypeDilemmaHandler.generalInstruction = "cancel";
    
    preOpRefresh = SubcommandUtil.getPreopRefreshPolicy(config);
    
    resourcePropertyChanges = new ParmsResourcePropertyChange[paths.size()];
    ResourceType resourceType; for (int i = 0; i < paths.size(); i++) {
      ILocation path = (ILocation)paths.get(i);
      ParmsResourcePropertyChange change = new ParmsResourcePropertyChange();
      File cfaRootFile = SubcommandUtil.findAncestorCFARoot((File)path.getAdapter(File.class));
      if (cfaRootFile == null) {
        throw StatusHelper.disallowed(NLS.bind(Messages.PropertySetCmd_CouldNotFindSandboxForPath, path.toOSString()));
      }
      
      resourceType = SubcommandUtil.getResourceType(path, null);
      if ((resourceType != ResourceType.FILE) && (
        (key.equals("jazz.executable")) || (key.equals("jazz.line-delimiter")) || 
        (key.equals("jazz.mime")) || (key.equals("jazz.encoding")))) {
        throw StatusHelper.disallowed(NLS.bind(Messages.PropertySetCmd_PropCanBeOnlySetOnFile, key, path.toOSString()));
      }
      
      ILocation cfaRoot = new PathLocation(cfaRootFile.getAbsolutePath());
      IRelativeLocation filePath = path.getLocationRelativeTo(cfaRoot);
      
      SubcommandUtil.registerSandboxes(new String[] { cfaRoot.toOSString() }, client, config);
      
      sandboxPath = cfaRoot.toOSString();
      filePath = filePath.toString();
      if ("jazz.executable".equals(key)) {
        executable = Boolean.valueOf(Boolean.parseBoolean(normalizedValue));
      } else if (key.equals("jazz.line-delimiter")) {
        lineDelimiter = normalizedValue;
      } else if (key.equals("jazz.mime")) {
        mimeType = normalizedValue;
      } else if (key.equals("jazz.link-type")) {
        linkType = normalizedValue;
      } else {
        propertyChanges = new ParmsProperty[1];
        ParmsProperty parmsProp = new ParmsProperty();
        propertyName = key;
        propertyValue = value;
        propertyChanges[0] = parmsProp;
      }
      
      resourcePropertyChanges[i] = change;
      if (force) {
        ParmsLineDelimiterErrorInstructions parmsLineDelimiterErrorInstructions = new ParmsLineDelimiterErrorInstructions();
        sandboxPath = cfaRoot.toOSString();
        filePath = filePath.toString();
        forceConsistentDelimiters = Boolean.valueOf(true);
        lineDelimiterDilemmaHandler.lineDelimiterErrorInstructions[i] = parmsLineDelimiterErrorInstructions;
      }
    }
    
    ResourcePropertyChangeResultDTO result = client.postResourceProperties(parms, null);
    List<LineDelimiterErrorDTO> lineDelimiterFailures = new ArrayList();
    
    List<LineDelimiterErrorDTO> lineDelimiterFailures2 = result.getLineDelimiterFailures();
    String path; for (LineDelimiterErrorDTO ldError : lineDelimiterFailures2) {
      boolean error = true;
      if ((force) && (ldError.getLineDelimiter().equals(normalizedValue))) {
        error = false;
      }
      if (error) {
        path = StringUtil.createPathString(ldError.getFileName().getSegments());
        config.getContext().stderr().println(NLS.bind(Messages.PropertySetCmd_PathContainsInconsistentDelimiters, 
          new String[] { path, key, value }));
        lineDelimiterFailures.add(ldError);
      }
    }
    

    IndentingPrintStream printStream = new IndentingPrintStream(config.getContext().stderr());
    

    List<MimeTypeErrorDTO> mimeTypeFailures = result.getMimeTypeFailures();
    if (mimeTypeFailures.size() > 0) {
      printStream.println(NLS.bind(Messages.PropertySetCmd_InvalidMimeTypeForFiles, key));
      for (MimeTypeErrorDTO mimeTypeFailure : mimeTypeFailures)
      {
        IRelativeLocation path = new RelativeLocation(mimeTypeFailure.getFileName().getSegments());
        printStream.indent().println(path.toString());
      }
    }
    


    List<PropertyFailureDTO> propertyFailures = result.getPropertyFailures();
    String reason = null;
    IRelativeLocation path; if (propertyFailures.size() > 0) {
      printStream.println(NLS.bind(Messages.PropertySetCmd_InvalidPropertyForFiles, key));
      for (PropertyFailureDTO propertyFailure : propertyFailures)
      {
        path = new RelativeLocation(propertyFailure.getFileName().getSegments());
        
        if (propertyFailure.getInvalidProperties() != null) {
          InvalidPropertyDTO invalidProp = (InvalidPropertyDTO)propertyFailure.getInvalidProperties().get(0);
          reason = invalidProp.getReason();
        }
        
        printStream.indent().println(path.toString());
      }
    }
    

    List<ShareableDTO> executableFailures = result.getExecutableFailures();
    if (executableFailures.size() > 0) {
      printStream.println(NLS.bind(Messages.PropertySetCmd_ExecutableErrorForFiles, key));
      for (ShareableDTO executableFailure : executableFailures)
      {
        IRelativeLocation path = new RelativeLocation(executableFailure.getRelativePath().getSegments());
        printStream.indent().println(path.toString());
      }
    }
    
    if (lineDelimiterFailures.size() > 0)
      throw StatusHelper.malformedInput(NLS.bind(Messages.PropertySetCmd_NResourcesHaveInconsistentDelimiters, Integer.toString(lineDelimiterFailures.size())));
    if (propertyFailures.size() > 0)
      throw StatusHelper.invalidProperty((reason != null) && (reason.length() > 0) ? 
        reason : NLS.bind(Messages.PropertySetCmd_InvalidProperty, key));
    if (mimeTypeFailures.size() > 0)
      throw StatusHelper.invalidMimeType(NLS.bind(Messages.PropertySetCmd_InvalidMimeType, key));
    if (executableFailures.size() > 0) {
      throw StatusHelper.invalidMimeType(NLS.bind(Messages.PropertySetCmd_ExecutableError, key));
    }
    config.getContext().stdout().println(NLS.bind(Messages.PropertySetCmd_KeySuccessfullySetToValue, key, value));
  }
}
