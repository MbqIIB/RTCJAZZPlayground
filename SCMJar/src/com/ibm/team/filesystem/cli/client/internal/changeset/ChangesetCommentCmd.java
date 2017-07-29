package com.ibm.team.filesystem.cli.client.internal.changeset;

import com.ibm.team.filesystem.cli.client.AbstractSubcommand;
import com.ibm.team.filesystem.cli.client.internal.Messages;
import com.ibm.team.filesystem.cli.core.internal.ScmCommandLineArgument;
import com.ibm.team.filesystem.cli.core.subcommands.IScmClientConfiguration;
import com.ibm.team.filesystem.cli.core.subcommands.IScmCommandLineArgument;
import com.ibm.team.filesystem.cli.core.util.RepoUtil;
import com.ibm.team.filesystem.cli.core.util.RepoUtil.ItemType;
import com.ibm.team.filesystem.cli.core.util.StatusHelper;
import com.ibm.team.filesystem.cli.core.util.SubcommandUtil;
import com.ibm.team.filesystem.client.FileSystemException;
import com.ibm.team.filesystem.client.rest.IFilesystemRestClient;
import com.ibm.team.filesystem.client.rest.parameters.ParmsChangeSet;
import com.ibm.team.filesystem.client.rest.parameters.ParmsSetChangeSetComment;
import com.ibm.team.repository.client.ITeamRepository;
import com.ibm.team.repository.common.TeamRepositoryException;
import com.ibm.team.repository.common.UUID;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IExecutionContext;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.ICommandLine;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.IPositionalOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.util.IndentingPrintStream;
import com.ibm.team.scm.common.IChangeSet;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

public class ChangesetCommentCmd extends AbstractSubcommand
{
  public ChangesetCommentCmd() {}
  
  public void run() throws FileSystemException
  {
    ICommandLine cli = config.getSubcommandCommandLine();
    String comment = cli.getOption(ChangesetCommentCmdOptions.OPT_COMMENT.getId());
    IScmCommandLineArgument csSelector = ScmCommandLineArgument.create(
      cli.getOptionValue(ChangesetCommonOptions.OPT_CHANGESET.getId()), config);
    
    List<IScmCommandLineArgument> csSelectors = new ArrayList(1);
    csSelectors.add(csSelector);
    setComment(comment, csSelectors, config);
    

    config.getContext().stdout().println(Messages.ChangesetCommentCmd_3);
  }
  
  public static void setComment(String comment, List<IScmCommandLineArgument> csSelectors, IScmClientConfiguration config) throws FileSystemException
  {
    SubcommandUtil.validateArgument(csSelectors, RepoUtil.ItemType.CHANGESET);
    
    IFilesystemRestClient client = SubcommandUtil.setupDaemon(config);
    
    Map<ITeamRepository, List<IChangeSet>> repo2CsList = 
      new HashMap();
    
    IChangeSet changeSet;
    for (IScmCommandLineArgument csSelector : csSelectors) {
      ITeamRepository repo = RepoUtil.loginUrlArgAncestor(config, client, csSelector);
      

      changeSet = RepoUtil.findChangeSet(csSelector.getItemSelector(), repo, config);
      
      List<IChangeSet> csList = (List)repo2CsList.get(repo);
      if (csList == null) {
        csList = new ArrayList();
        repo2CsList.put(repo, csList);
      }
      if (!csList.contains(changeSet)) {
        csList.add(changeSet);
      }
    }
    

    for (??? = repo2CsList.entrySet().iterator(); ???.hasNext(); 
        changeSet.hasNext())
    {
      Map.Entry<ITeamRepository, List<IChangeSet>> entry = (Map.Entry)???.next();
      changeSet = ((List)entry.getValue()).iterator(); continue;IChangeSet cs = (IChangeSet)changeSet.next();
      setComment(cs.getItemId().getUuidValue(), comment, ((ITeamRepository)entry.getKey()).getRepositoryURI(), 
        client, config);
    }
  }
  
  public static void setComment(String csId, String comment, String repoUri, IFilesystemRestClient client, IScmClientConfiguration config)
    throws FileSystemException
  {
    ParmsSetChangeSetComment parmsComment = new ParmsSetChangeSetComment();
    changeSet = new ParmsChangeSet();
    changeSet.changeSetItemId = csId;
    changeSet.repositoryUrl = repoUri;
    comment = comment;
    try
    {
      client.postSetChangeSetComment(parmsComment, null);
    } catch (TeamRepositoryException e) {
      throw StatusHelper.wrap(Messages.ChangesetCommentCmd_2, e, new IndentingPrintStream(
        config.getContext().stderr()), repoUri);
    }
  }
}
