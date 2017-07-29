package com.ibm.team.filesystem.cli.client.internal.property;

import com.ibm.team.filesystem.cli.client.internal.Messages;
import com.ibm.team.filesystem.cli.core.subcommands.IScmClientConfiguration;
import com.ibm.team.filesystem.cli.core.util.RepoUtil;
import com.ibm.team.filesystem.cli.core.util.StatusHelper;
import com.ibm.team.filesystem.cli.core.util.SubcommandUtil;
import com.ibm.team.filesystem.client.FileSystemException;
import com.ibm.team.filesystem.client.ILocalChange;
import com.ibm.team.filesystem.client.ILocation;
import com.ibm.team.filesystem.client.IRelativeLocation;
import com.ibm.team.filesystem.client.IShareable;
import com.ibm.team.filesystem.client.ResourceType;
import com.ibm.team.filesystem.client.internal.PathLocation;
import com.ibm.team.filesystem.client.internal.RelativeLocation;
import com.ibm.team.filesystem.client.internal.SharingManager;
import com.ibm.team.filesystem.client.rest.IFilesystemRestClient;
import com.ibm.team.filesystem.client.rest.parameters.ParmsProperty;
import com.ibm.team.filesystem.client.rest.parameters.ParmsResourcePropertyChange;
import com.ibm.team.filesystem.client.rest.parameters.ParmsResourcePropertyUpdates;
import com.ibm.team.filesystem.common.internal.rest.client.core.PathDTO;
import com.ibm.team.filesystem.common.internal.rest.client.resource.InvalidPropertyDTO;
import com.ibm.team.filesystem.common.internal.rest.client.resource.PropertyFailureDTO;
import com.ibm.team.filesystem.common.internal.rest.client.resource.ResourcePropertyChangeResultDTO;
import com.ibm.team.repository.common.TeamRepositoryException;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IExecutionContext;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.OptionKey;
import com.ibm.team.rtc.cli.infrastructure.internal.util.IndentingPrintStream;
import java.io.File;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.eclipse.osgi.util.NLS;






public class VersionableSetCustomAttributesCmd
{
  public static final OptionKey OPT_FILES = new OptionKey("files");
  public static final OptionKey OPT_KEY = new OptionKey("key");
  public static final OptionKey OPT_VALUE = new OptionKey("value");
  

  private IFilesystemRestClient client;
  

  public VersionableSetCustomAttributesCmd() {}
  

  public static void setProperty(IScmClientConfiguration config, String key, String value, IFilesystemRestClient client, List<ILocation> paths)
    throws TeamRepositoryException, FileSystemException
  {
    RepoUtil.loginUrlArgAncOrOnPaths(config, client, paths);
    
    ParmsResourcePropertyUpdates parms = new ParmsResourcePropertyUpdates();
    
    resourcePropertyChanges = new ParmsResourcePropertyChange[paths.size()];
    Map<ILocation, List<ILocation>> sandbox2FileMap = new HashMap(paths.size());
    File cfaRootFile; for (int i = 0; i < paths.size(); i++) {
      path = (ILocation)paths.get(i);
      ParmsResourcePropertyChange change = new ParmsResourcePropertyChange();
      cfaRootFile = SubcommandUtil.findAncestorCFARoot((File)path.getAdapter(File.class));
      if (cfaRootFile == null) {
        throw StatusHelper.disallowed(NLS.bind(Messages.PropertySetCmd_CouldNotFindSandboxForPath, path.toOSString()));
      }
      
      ILocation cfaRoot = new PathLocation(cfaRootFile.getAbsolutePath());
      IRelativeLocation filePath = path.getLocationRelativeTo(cfaRoot);
      
      SubcommandUtil.registerSandboxes(new String[] { cfaRoot.toOSString() }, client, config);
      
      sandboxPath = cfaRoot.toOSString();
      filePath = filePath.toString();
      
      if (!sandbox2FileMap.containsKey(cfaRoot)) {
        List<ILocation> pathList = new ArrayList();
        pathList.add(path);
        sandbox2FileMap.put(cfaRoot, pathList);
      } else {
        ((List)sandbox2FileMap.get(cfaRoot)).add(path);
      }
      
      propertyChanges = new ParmsProperty[1];
      ParmsProperty parmsProp = new ParmsProperty();
      propertyName = key;
      propertyValue = value;
      propertyChanges[0] = parmsProp;
      
      resourcePropertyChanges[i] = change;
    }
    
    ILocalChange pendingChange;
    for (ILocation path = sandbox2FileMap.keySet().iterator(); path.hasNext(); 
        

        cfaRootFile.hasNext())
    {
      ILocation sandbox = (ILocation)path.next();
      SubcommandUtil.refreshPaths(sandbox, (List)sandbox2FileMap.get(sandbox), client, config);
      
      cfaRootFile = ((List)sandbox2FileMap.get(sandbox)).iterator(); continue;ILocation path = (ILocation)cfaRootFile.next();
      IShareable shareable = SharingManager.getInstance().findShareable(path, ResourceType.FILE);
      pendingChange = shareable.getChange(null);
      if (pendingChange.getType() != 0) {
        throw StatusHelper.disallowed(NLS.bind(Messages.ExtendedPropertySetCmd_UnresolvedChanges, path.getName()));
      }
    }
    

    ResourcePropertyChangeResultDTO result = client.postExtendedProperties(parms, null);
    

    IndentingPrintStream printStream = new IndentingPrintStream(config.getContext().stderr());
    


    List<PropertyFailureDTO> propertyFailures = result.getPropertyFailures();
    String reason = null;
    if (propertyFailures.size() > 0) {
      printStream.println(NLS.bind(Messages.ExtendedPropertySetCmd_InvalidPropertyForFiles, key));
      for (PropertyFailureDTO propertyFailure : propertyFailures)
      {
        IRelativeLocation path = new RelativeLocation(propertyFailure.getFileName().getSegments());
        
        if (propertyFailure.getInvalidProperties() != null) {
          InvalidPropertyDTO invalidProp = (InvalidPropertyDTO)propertyFailure.getInvalidProperties().get(0);
          reason = invalidProp.getReason();
        }
        
        printStream.indent().println(path.toString());
      }
    }
    

    if (propertyFailures.size() > 0) {
      throw StatusHelper.invalidProperty((reason != null) && (reason.length() > 0) ? 
        reason : NLS.bind(Messages.PropertySetCmd_InvalidProperty, key));
    }
    config.getContext().stdout().println(NLS.bind(Messages.PropertySetCmd_KeySuccessfullySetToValue, key, value));
  }
}
