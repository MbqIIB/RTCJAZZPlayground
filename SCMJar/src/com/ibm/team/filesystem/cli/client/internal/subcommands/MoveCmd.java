package com.ibm.team.filesystem.cli.client.internal.subcommands;

import com.ibm.team.filesystem.cli.client.AbstractSubcommand;
import com.ibm.team.filesystem.cli.client.internal.Messages;
import com.ibm.team.filesystem.cli.core.subcommands.IScmClientConfiguration;
import com.ibm.team.filesystem.cli.core.util.RepoUtil;
import com.ibm.team.filesystem.cli.core.util.StatusHelper;
import com.ibm.team.filesystem.cli.core.util.SubcommandUtil;
import com.ibm.team.filesystem.client.FileSystemException;
import com.ibm.team.filesystem.client.ILocation;
import com.ibm.team.filesystem.client.IRelativeLocation;
import com.ibm.team.filesystem.client.internal.PathLocation;
import com.ibm.team.filesystem.client.rest.IFilesystemRestClient;
import com.ibm.team.filesystem.client.rest.parameters.ParmsMoveResource;
import com.ibm.team.filesystem.client.rest.parameters.ParmsMoveResources;
import com.ibm.team.filesystem.common.internal.rest.client.core.PathDTO;
import com.ibm.team.filesystem.common.internal.rest.client.core.ShareableDTO;
import com.ibm.team.filesystem.common.internal.rest.client.resource.SandboxPathsResultDTO;
import com.ibm.team.repository.common.PermissionDeniedException;
import com.ibm.team.repository.common.TeamRepositoryException;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IExecutionContext;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.ICommandLine;
import com.ibm.team.rtc.cli.infrastructure.internal.util.IndentingPrintStream;
import com.ibm.team.rtc.cli.infrastructure.internal.util.StringUtil;
import java.io.File;
import java.io.PrintStream;
import java.util.Collections;
import java.util.List;
import org.eclipse.osgi.util.NLS;




public class MoveCmd
  extends AbstractSubcommand
{
  public MoveCmd() {}
  
  public void run()
    throws FileSystemException
  {
    IFilesystemRestClient client = SubcommandUtil.setupDaemon(config);
    

    ICommandLine cli = config.getSubcommandCommandLine();
    
    String srcOpt = cli.getOption(MoveCmdOpts.OPT_SOURCE);
    ILocation source = SubcommandUtil.makeAbsolutePath(config, srcOpt);
    
    File sourceFile = (File)source.getAdapter(File.class);
    if (!sourceFile.exists()) {
      throw StatusHelper.argSyntax(NLS.bind(Messages.Common_PATH_DOES_NOT_EXIST, srcOpt));
    }
    


    SandboxPathsResultDTO pathsResult = RepoUtil.getSandboxPathsAndRegister(source.toOSString(), client, config);
    ShareableDTO shareableSource = (ShareableDTO)pathsResult.getPaths().get(0);
    

    if (shareableSource.getRelativePath().getSegments().size() == 1) {
      throw StatusHelper.moveFailure(Messages.MoveCmd_PROJECT_FOLDER_MOVE_NOT_SUPPORTED);
    }
    

    if (SubcommandUtil.shouldRefreshFileSystem(config)) {
      ILocation sandboxLoc = new PathLocation(shareableSource.getSandboxPath());
      new PathLocation(
        StringUtil.createPathString(shareableSource.getRelativePath().getSegments()));
      SubcommandUtil.refreshPaths(sandboxLoc, Collections.singletonList(source), client, config);
    }
    

    if (!sourceFile.exists()) {
      throw StatusHelper.argSyntax(NLS.bind(Messages.Common_PATH_DOES_NOT_EXIST, srcOpt));
    }
    

    String tgtOpt = cli.getOption(MoveCmdOpts.OPT_TARGET);
    ILocation target = SubcommandUtil.makeAbsolutePath(config, tgtOpt);
    

    File targetParentFile = (File)target.getParent().getAdapter(File.class);
    if (!targetParentFile.exists()) {
      throw StatusHelper.moveFailure(NLS.bind(Messages.MoveCmd_TARGET_HAS_NO_PARENT, tgtOpt));
    }
    

    pathsResult = RepoUtil.getSandboxPathsAndRegister(target.toOSString(), client, config);
    ShareableDTO shareableTarget = (ShareableDTO)pathsResult.getPaths().get(0);
    

    if (shareableTarget.getRelativePath().getSegments().size() == 0) {
      throw StatusHelper.moveFailure(Messages.MoveCmd_PROJECT_FOLDER_MOVE_NOT_SUPPORTED);
    }
    

    if (SubcommandUtil.shouldRefreshFileSystem(config)) {
      ILocation sandboxLoc = new PathLocation(shareableTarget.getSandboxPath());
      new PathLocation(
        StringUtil.createPathString(shareableTarget.getRelativePath().getSegments()));
      SubcommandUtil.refreshPaths(sandboxLoc, Collections.singletonList(target), client, config);
    }
    

    if (!targetParentFile.exists()) {
      throw StatusHelper.argSyntax(NLS.bind(Messages.Common_PATH_DOES_NOT_EXIST, tgtOpt));
    }
    

    File targetFile = (File)target.getAdapter(File.class);
    if ((!targetFile.exists()) && (shareableTarget.getRelativePath().getSegments().size() == 1)) {
      throw StatusHelper.moveFailure(Messages.MoveCmd_PROJECT_FOLDER_MOVE_NOT_SUPPORTED);
    }
    

    if (!shareableTarget.getSandboxPath().equals(shareableSource.getSandboxPath())) {
      throw StatusHelper.argSyntax(Messages.MoveCmd_MOVE_ACROSS_SANDBOXES_NOT_ALLOWED);
    }
    

    if (source.isPrefixOf(target)) {
      throw StatusHelper.disallowed(Messages.MoveCmd_SOURCE_IS_ANCESTOR_OF_TARGET);
    }
    

    IRelativeLocation sourceRelativeToSandbox = source.getLocationRelativeTo(new PathLocation(shareableSource.getSandboxPath()));
    target.getLocationRelativeTo(new PathLocation(shareableTarget.getSandboxPath()));
    
    if (targetFile.exists())
    {
      if (targetFile.isDirectory()) {
        ILocation wouldBeTarget = target.append(sourceRelativeToSandbox.getName());
        
        File wouldBeTargetFile = (File)wouldBeTarget.getAdapter(File.class);
        if (wouldBeTargetFile.exists()) {
          throw StatusHelper.moveFailure(NLS.bind(Messages.MoveCmd_TARGET_FILE_OR_FOLDER_EXISTS, sourceRelativeToSandbox.getName()));
        }
      }
      else {
        throw StatusHelper.moveFailure(NLS.bind(Messages.MoveCmd_4, tgtOpt));
      }
    }
    

    if (source.isPrefixOf(target)) {
      throw StatusHelper.moveFailure(Messages.MoveCmd_5);
    }
    

    String relTargetPath = StringUtil.createPathString(shareableTarget.getRelativePath().getSegments());
    if (targetFile.exists()) {
      relTargetPath = relTargetPath + '/' + source.getName();
    }
    ParmsMoveResources moveParms = new ParmsMoveResources(new ParmsMoveResource[] { new ParmsMoveResource(shareableSource.getSandboxPath(), 
      StringUtil.createPathString(shareableSource.getRelativePath().getSegments()), relTargetPath) });
    try
    {
      client.postMoveResources(moveParms, null);
    } catch (TeamRepositoryException e) {
      PermissionDeniedException pde = (PermissionDeniedException)SubcommandUtil.findExceptionByType(PermissionDeniedException.class, e);
      if (pde != null) {
        throw StatusHelper.permissionFailure(pde, new IndentingPrintStream(config.getContext().stderr()));
      }
      
      throw StatusHelper.wrap(NLS.bind(Messages.MoveCmd_9, srcOpt), e, new IndentingPrintStream(config.getContext().stderr()), null);
    }
    config.getContext().stdout().println(Messages.MoveCmd_SUCCESSFULLY_COMPLETED);
  }
}
