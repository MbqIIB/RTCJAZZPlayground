package com.ibm.team.filesystem.cli.client.internal.subcommands;

import com.ibm.team.filesystem.cli.client.AbstractSubcommand;
import com.ibm.team.filesystem.cli.client.internal.Messages;
import com.ibm.team.filesystem.cli.core.Constants;
import com.ibm.team.filesystem.cli.core.internal.ScmCommandLineArgument;
import com.ibm.team.filesystem.cli.core.subcommands.CommonOptions;
import com.ibm.team.filesystem.cli.core.subcommands.IScmClientConfiguration;
import com.ibm.team.filesystem.cli.core.subcommands.IScmCommandLineArgument;
import com.ibm.team.filesystem.cli.core.util.RepoUtil.ItemType;
import com.ibm.team.filesystem.cli.core.util.StatusHelper;
import com.ibm.team.filesystem.cli.core.util.SubcommandUtil;
import com.ibm.team.filesystem.client.FileSystemException;
import com.ibm.team.filesystem.client.ILocation;
import com.ibm.team.filesystem.client.IRelativeLocation;
import com.ibm.team.filesystem.client.ResourceType;
import com.ibm.team.filesystem.client.internal.PathLocation;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IExecutionContext;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.ICommandLine;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.IOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.Options;
import java.io.File;
import java.util.List;
import org.eclipse.core.runtime.Path;
import org.eclipse.osgi.util.NLS;




























public class LoadCmdLauncher
  extends AbstractSubcommand
{
  public LoadCmdLauncher() {}
  
  public void run()
    throws FileSystemException
  {
    List<IScmCommandLineArgument> cSelectors = null;
    




    ICommandLine subargs = config.getSubcommandCommandLine();
    


    IScmCommandLineArgument wsSelector = ScmCommandLineArgument.create(subargs.getOptionValue(LoadCmdOptions.OPT_WORKSPACE_SELECTOR), config);
    SubcommandUtil.validateArgument(wsSelector, RepoUtil.ItemType.WORKSPACE);
    
    String loadTargetStr = subargs.getOption(LoadCmdOptions.OPT_LOAD_TARGET, null);
    
    String alternativeName = subargs.getOption(LoadCmdOptions.OPT_ALTERNATIVE_NAME, null);
    
    boolean getAll = subargs.hasOption(CommonOptions.OPT_ALL);
    
    if ((getAll) && (alternativeName != null)) {
      throw StatusHelper.argSyntax(NLS.bind(Messages.LoadCmdLauncher_TOO_MANY_COMPONENTS, subargs.getDefinition().getOption(LoadCmdOptions.OPT_ALTERNATIVE_NAME).getName()));
    }
    
    if (subargs.hasOption(LoadCmdOptions.OPT_REMOTE_PATHS)) {
      cSelectors = ScmCommandLineArgument.createList(subargs.getOptionValues(LoadCmdOptions.OPT_REMOTE_PATHS), config);
      SubcommandUtil.validateArgument(cSelectors, RepoUtil.ItemType.COMPONENT);
      
      if ((cSelectors.size() > 1) && (loadTargetStr != null)) {
        throw StatusHelper.argSyntax(NLS.bind(Messages.LoadCmdLauncher_TOO_MANY_REMOTE_PATHS, subargs.getDefinition().getOption(LoadCmdOptions.OPT_LOAD_TARGET).getName()));
      }
      
      if ((cSelectors.size() > 1) && (alternativeName != null)) {
        throw StatusHelper.argSyntax(NLS.bind(Messages.LoadCmdLauncher_TOO_MANY_REMOTE_PATHS, subargs.getDefinition().getOption(LoadCmdOptions.OPT_ALTERNATIVE_NAME).getName()));
      }
    }
    
    if ((cSelectors == null) && (alternativeName != null)) {
      throw StatusHelper.argSyntax(NLS.bind(Messages.LoadCmdLauncher_NO_REMOTE_PATHS, subargs.getDefinition().getOption(LoadCmdOptions.OPT_ALTERNATIVE_NAME).getName()));
    }
    
    boolean loadCompRoots = subargs.hasOption(LoadCmdOptions.OPT_LOAD_COMPONENT_ROOTS);
    
    if ((loadCompRoots) && (alternativeName != null)) {
      throw StatusHelper.argSyntax(NLS.bind(Messages.LoadCmdLauncher_SPECIFY_ONE_OF_COMPROOT_OR_ALTNAME, 
        subargs.getDefinition().getOption(LoadCmdOptions.OPT_LOAD_COMPONENT_ROOTS).getName(), 
        subargs.getDefinition().getOption(LoadCmdOptions.OPT_ALTERNATIVE_NAME).getName()));
    }
    
    boolean quiet = subargs.hasOption(CommonOptions.OPT_QUIET);
    
    LoadRuleConfig loadRuleConfig = consumeLoadRuleArgs(subargs, cSelectors);
    



    IRelativeLocation loadTarget = null;
    

    File cwd = new File(config.getContext().getCurrentWorkingDirectory());
    File loc = null;
    if ((subargs.hasOption(CommonOptions.OPT_DIRECTORY)) || 
      (!subargs.hasOption(LoadCmdOptions.OPT_DIR))) {
      loc = cwd;
    } else {
      loc = new File(SubcommandUtil.makeAbsolutePath(cwd.getAbsolutePath(), 
        subargs.getOption(LoadCmdOptions.OPT_DIR)).toOSString());
    }
    
    File cfaAncestor = SubcommandUtil.findAncestorCFARoot(loc);
    File cfaRoot;
    File cfaRoot; if (cfaAncestor == null)
    {
      ResourceType resourceType = SubcommandUtil.getResourceType(new Path(loc.getPath()), null);
      if (resourceType == null) {
        if (!loc.mkdirs()) {
          throw StatusHelper.misconfiguredLocalFS(
            NLS.bind(Messages.LoadCmdLauncher_27, loc.getAbsolutePath()));
        }
      } else if (resourceType != ResourceType.FOLDER) {
        throw StatusHelper.misconfiguredLocalFS(
          NLS.bind(Messages.LoadCmdLauncher_NOT_A_FOLDER, loc.getAbsolutePath()));
      }
      
      cfaRoot = loc;

    }
    else
    {
      if (!loc.equals(cfaAncestor)) {
        String metaDataName = "";
        for (String name : Constants.METADATA_ROOT_NAMES) {
          File metadir = new File(cfaAncestor, name);
          if (metadir.exists()) {
            metaDataName = name;
          }
        }
        throw StatusHelper.argSyntax(NLS.bind(Messages.LoadCmdLauncher_MUST_LOAD_IN_ROOT_OR_SPECIFY_PATH, new String[] { cfaAncestor.getAbsolutePath(), metaDataName, config.getSubcommandCommandLine().getDefinition().getOption(LoadCmdOptions.OPT_LOAD_TARGET).getName() }));
      }
      
      cfaRoot = cfaAncestor;
    }
    
    if (!SubcommandUtil.isEclipseWorkspaceRootSet())
    {

      SubcommandUtil.initializeEclipseWorkspaceRoot(cfaRoot);
    }
    
    ILocation cfaRootPath = new PathLocation(SubcommandUtil.canonicalize(cfaRoot).getAbsolutePath());
    

    if (loadTargetStr != null)
    {
      File loadTargetFile = new File(loadTargetStr);
      if (!loadTargetFile.isAbsolute()) {
        loadTargetFile = new File(cwd, loadTargetStr);
      }
      

      ILocation fullLoadTarget = new PathLocation(SubcommandUtil.canonicalize(loadTargetFile).getAbsolutePath());
      
      if (!cfaRootPath.isPrefixOf(fullLoadTarget)) {
        throw StatusHelper.argSyntax(NLS.bind(Messages.LoadCmdLauncher_LOAD_TARGET_PATH_NOT_DESCENDENT_OF_SANDBOX_PATH, fullLoadTarget.toOSString(), cfaRootPath.toOSString()));
      }
      

      loadTarget = fullLoadTarget.getLocationRelativeTo(cfaRootPath);
    }
    


    new LoadCmd().run(config, cfaRootPath, wsSelector, cSelectors, getAll, loadCompRoots, loadTarget, loadRuleConfig, quiet, alternativeName);
  }
  
  private LoadRuleConfig consumeLoadRuleArgs(ICommandLine subargs, List<IScmCommandLineArgument> cSelectors) throws FileSystemException
  {
    boolean hasLocal = subargs.hasOption(LoadCmdOptions.OPT_LOCAL_LOADRULE_PATH);
    boolean hasRemote = subargs.hasOption(LoadCmdOptions.OPT_REMOTE_LOADRULE_PATH);
    

    if ((!hasLocal) && (!hasRemote)) {
      return null;
    }
    
    if ((hasLocal) && (hasRemote)) {
      String remoteName = subargs.getDefinition().getOption(LoadCmdOptions.OPT_REMOTE_LOADRULE_PATH).getName();
      String localName = subargs.getDefinition().getOption(LoadCmdOptions.OPT_LOCAL_LOADRULE_PATH).getName();
      throw StatusHelper.argSyntax(NLS.bind(Messages.LoadCmdLauncher_1, localName, remoteName));
    }
    
    if ((subargs.hasOption(LoadCmdOptions.OPT_ALTERNATIVE_NAME)) || (subargs.hasOption(LoadCmdOptions.OPT_LOAD_COMPONENT_ROOTS)) || 
      (subargs.hasOption(LoadCmdOptions.OPT_LOAD_TARGET))) {
      throw StatusHelper.argSyntax(NLS.bind(Messages.LoadCmdLauncher_LOAD_RULE_INVALID_OPTIONS, 
        new String[] {
        subargs.getDefinition().getOption(LoadCmdOptions.OPT_ALTERNATIVE_NAME).getName(), 
        subargs.getDefinition().getOption(LoadCmdOptions.OPT_LOAD_COMPONENT_ROOTS).getName(), 
        subargs.getDefinition().getOption(LoadCmdOptions.OPT_LOAD_TARGET).getName(), 
        subargs.getDefinition().getOption(LoadCmdOptions.OPT_LOCAL_LOADRULE_PATH).getName(), 
        subargs.getDefinition().getOption(LoadCmdOptions.OPT_REMOTE_LOADRULE_PATH).getName() }));
    }
    

    LoadRuleConfig rule;
    
    if (hasRemote) {
      LoadRuleConfig rule = new RemoteLoadRuleConfig();
      
      remotePath = subargs.getOption(LoadCmdOptions.OPT_REMOTE_LOADRULE_PATH);
    }
    else {
      assert (hasLocal);
      
      rule = new LocalLoadRuleConfig();
      
      localPath = subargs.getOption(LoadCmdOptions.OPT_LOCAL_LOADRULE_PATH);
    }
    
    return rule;
  }
  
  public static class LoadRuleConfig
  {
    public LoadRuleConfig() {}
  }
  
  public static class LocalLoadRuleConfig
    extends LoadCmdLauncher.LoadRuleConfig
  {
    public String localPath;
    
    public LocalLoadRuleConfig() {}
  }
  
  public static class RemoteLoadRuleConfig
    extends LoadCmdLauncher.LoadRuleConfig
  {
    public String remotePath;
    
    public RemoteLoadRuleConfig() {}
  }
}
