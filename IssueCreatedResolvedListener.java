package ru.hz.name.telegram.plugins.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Iterator;
import java.util.StringJoiner;

import java.nio.charset.StandardCharsets;

import java.util.Locale;
import java.io.InputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;

import javax.inject.Inject;
import javax.inject.Named;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;

import com.atlassian.event.api.EventListener;
import com.atlassian.event.api.EventPublisher;
import com.atlassian.jira.event.issue.IssueEvent;
import com.atlassian.jira.event.type.EventType;
import com.atlassian.jira.issue.Issue;
import com.atlassian.plugin.spring.scanner.annotation.export.ExportAsService;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;

import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.user.util.UserUtil;
import com.atlassian.jira.user.UserPropertyManager;
import com.atlassian.jira.user.util.UserManager;
import com.atlassian.jira.issue.watchers.WatcherManager;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.jira.issue.comments.CommentManager;
import com.atlassian.jira.issue.comments.CommentPermissionManager;
import com.atlassian.jira.issue.changehistory.ChangeHistoryManager;
import com.atlassian.jira.issue.IssueFieldConstants;
import com.atlassian.jira.issue.changehistory.ChangeHistoryItem;
import com.atlassian.jira.issue.history.ChangeItemBean;
import com.atlassian.jira.event.issue.MentionIssueEvent;

import com.atlassian.jira.issue.CustomFieldManager;
import com.atlassian.jira.issue.customfields.CustomFieldType;
import com.atlassian.jira.issue.fields.CustomField;

import com.atlassian.jira.security.PermissionManager;
import com.atlassian.jira.security.plugin.ProjectPermissionKey;

import com.atlassian.jira.ComponentManager;

/*
  *
  * @author Andrei Kondratiev andry.kondratiev@gmail.com
  *
*/
@ExportAsService ({IssueCreatedResolvedListener.class})
@Named ("eventListener")

public class IssueCreatedResolvedListener implements InitializingBean, DisposableBean{
  public static final Locale en = new Locale("en");
  private CommentPermissionManager commentPermissionManager;

  @ComponentImport
  private final EventPublisher eventPublisher;

  @Inject
  public IssueCreatedResolvedListener(final EventPublisher eventPublisher) {
    this.eventPublisher = eventPublisher;
  }

  /**
  * Called when the plugin has been enabled.
  * @throws Exception
  */
  @Override
  public void afterPropertiesSet() throws Exception {
  // register ourselves with the EventPublisher
    eventPublisher.register(this);
  }

  /**
  * Called when the plugin is being disabled or removed.
  * @throws Exception
  */
  @Override
  public void destroy() throws Exception {
    // unregister ourselves with the EventPublisher
    eventPublisher.unregister(this);
  }

  @EventListener
  public void onIssueEvent(IssueEvent issueEvent) {
    Long eventTypeId = issueEvent.getEventTypeId();
    Issue issue = issueEvent.getIssue();
    UserUtil userUtil = new ComponentAccessor().getUserUtil();
    UserPropertyManager userPropertyManager = new ComponentAccessor().getUserPropertyManager();
    UserManager userManager = new ComponentAccessor().getUserManager();
    PermissionManager permissionManager = new ComponentAccessor().getPermissionManager();
    WatcherManager watcherManager = new ComponentAccessor().getWatcherManager();
    List<ApplicationUser> watchers = watcherManager.getWatchers(issue, en);
    StringBuilder telegramChatid = new StringBuilder();
    CommentManager commentManager = new ComponentAccessor().getCommentManager();
    ChangeHistoryManager changeHistoryManager = new ComponentAccessor().getChangeHistoryManager();
    String eventType = null;
    String messageToTelegram = null;
    String userCreatedEventChatID = userPropertyManager.getPropertySet(issueEvent.getUser()).getString("jira.meta.chat");
    CustomFieldManager customFieldManager = new ComponentAccessor().getCustomFieldManager();
    ApplicationUser issueCreator = issue.getCreator();
    ProjectPermissionKey projectPermissionKey = new ProjectPermissionKey("BROWSE_PROJECTS");
    commentPermissionManager = ComponentManager.getComponentInstanceOfType(CommentPermissionManager.class);


    if (userCreatedEventChatID == null) {
      userCreatedEventChatID = "000000";
    }

    messageToTelegram = "Проект: <a href=\"https://JIRAURL.CHANGEIT/projects/" + issue.getProjectObject().getKey() + "/issues/\">[" + issue.getProjectObject().getKey() + "] " + issue.getProjectObject().getName() + "</a>\nЗадача: <a href=\"https://JIRAURL.CHANGEIT/browse/" + issue.getKey() + "\">[" + issue.getKey() + "] " + escapeMessage(issue.getSummary()) + "</a>\n";

    if (eventTypeId.equals(EventType.ISSUE_CREATED_ID)) {
      eventType = "Новая задача";
      if (issue.getDescription() != null) {
        messageToTelegram += "Автор: <b>" + issue.getReporterUser().getDisplayName() + "</b>\nТип действия: <b>" + eventType  + "</b>" + "\nПриоритет: <b>" + issue.getPriorityObject().getName() + "</b>" + "\nТип: <b>" + issue.getIssueType().getName() + "</b>"  + "\nОписание: " + escapeMessage(issue.getDescription()) + "\n";
      } else {
        messageToTelegram += "Автор: <b>" + issue.getReporterUser().getDisplayName() + "</b>\nТип действия: <b>" + eventType  + "</b>" + "\nПриоритет: <b>" + issue.getPriorityObject().getName() + "</b>" + "\nТип: <b>" + issue.getIssueType().getName() + "</b>"  + "\n";
      }

    } else if (eventTypeId.equals(EventType.ISSUE_COMMENTED_ID)) {
      eventType = "Комментарий добавлен";
      messageToTelegram += "Автор: <b>" + commentManager.getLastComment(issue).getAuthorFullName() + "</b>\nТип действия: <b>" + eventType  + "</b>\n" + "Комментарий: " + escapeMessage(commentManager.getLastComment(issue).getBody()) + "\n";

    } else if (eventTypeId.equals(EventType.ISSUE_UPDATED_ID)) {
      eventType = "Задача обновлена";
      messageToTelegram += "Тип действия: <b>" + eventType  + "</b>" + "\nАвтор: <b>" + issueEvent.getUser().getDisplayName() + "</b>\n";

    } else if (eventTypeId.equals(EventType.ISSUE_ASSIGNED_ID)) {
      eventType = "Задача назначена";
      messageToTelegram += "Тип действия: <b>" + eventType  + "</b>\n" + "Автор: <b>" + issueEvent.getUser().getDisplayName() + "</b>\n";

    } else if (eventTypeId.equals(EventType.ISSUE_CLOSED_ID)) {
      eventType = "Задача закрыта";
      messageToTelegram += "Тип действия: <b>" + eventType  + "</b>\n" + "Автор: <b>" + issueEvent.getUser().getDisplayName() + "</b>\n";

    } else if (eventTypeId.equals(EventType.ISSUE_COMMENT_EDITED_ID)) {
      eventType = "Комментарий изменен";
      messageToTelegram += "Тип действия: <b>" + eventType  + "</b>\n" + "Автор: <b>" + issueEvent.getUser().getDisplayName() + "</b>\n";

    } else if (eventTypeId.equals(EventType.ISSUE_DELETED_ID)) {
      eventType = "Задача удалена";
      messageToTelegram += "Тип действия: <b>" + eventType  + "</b>\n" + "Автор: <b>" + issueEvent.getUser().getDisplayName() + "</b>\n";

    } else if (eventTypeId.equals(EventType.ISSUE_MOVED_ID)) {
      eventType = "Задача перемещена";
      messageToTelegram += "Тип действия: <b>" + eventType  + "</b>\n" + "Автор: <b>" + issueEvent.getUser().getDisplayName() + "</b>\n";

    } else if (eventTypeId.equals(EventType.ISSUE_REOPENED_ID)) {
      eventType = "Задача переоткрыта";
      messageToTelegram += "Тип действия: <b>" + eventType  + "</b>\n" + "Автор: <b>" + issueEvent.getUser().getDisplayName() + "</b>\n";

    } else if (eventTypeId.equals(EventType.ISSUE_RESOLVED_ID)) {
      eventType = "Задача решена";
      messageToTelegram += "Тип действия: <b>" + eventType  + "</b>\n" + "Автор: <b>" + issueEvent.getUser().getDisplayName() + "</b>\n";

    } else if (eventTypeId.equals(EventType.ISSUE_WORKSTARTED_ID)) {
      eventType = "Работа начата";
      messageToTelegram += "Тип действия: <b>" + eventType  + "</b>\n" + "Автор: <b>" + issueEvent.getUser().getDisplayName() + "</b>\n";

    } else if (eventTypeId.equals(EventType.ISSUE_WORKSTOPPED_ID)) {
      eventType = "Работа остановлена";
      messageToTelegram += "Тип действия: <b>" + eventType  + "</b>\n" + "Автор: <b>" + issueEvent.getUser().getDisplayName() + "</b>\n";

    } else if (eventTypeId == 13) {
      eventType = "Статус изменен";
      messageToTelegram += "Тип действия: <b>" + eventType  + "</b>\n" + "Статус: <b>" + issue.getStatus().getSimpleStatus().getName() + "</b>\n" + "Автор: <b>" + issueEvent.getUser().getDisplayName() + "</b>\n";

    } else if (eventTypeId == 10) {
      eventType = "Запись рабочего времени";
      messageToTelegram += "Тип действия: <b>" + eventType  + "</b>\n" + "Автор: <b>" + issueEvent.getUser().getDisplayName() + "</b>\n";

    } else if (eventTypeId == 16) {
      eventType = "Удаление рабочего времени";
      messageToTelegram += "Тип действия: <b>" + eventType  + "</b>\n" + "Автор: <b>" + issueEvent.getUser().getDisplayName() + "</b>\n";

    } else if (eventTypeId == 15) {
      eventType = "Изменение рабочего времени";
      messageToTelegram += "Тип действия: <b>" + eventType  + "</b>\n" + "Автор: <b>" + issueEvent.getUser().getDisplayName() + "</b>\n";
    }

    if (eventType == null) {
      eventType = eventTypeId.toString();
    }

    // Подписываем на отправку вотчерам
    for (ApplicationUser s : watchers) {
      try {
        String watcherTelegramChatid = userPropertyManager.getPropertySet(s).getString("jira.meta.chat");
        System.out.println("TELEGRAM WATCHER ID: " + watcherTelegramChatid + " USER CREATED EVENT ID: " + userCreatedEventChatID);
        // Если у вотчера есть чатид, и не он создал событие и еще есть права на просмотр задачи
        if (watcherTelegramChatid.matches("-?[0-9]+") && !watcherTelegramChatid.equals(userCreatedEventChatID) && permissionManager.hasPermission(projectPermissionKey, issue, s)) {
          // Если имеем дело с комментариями, то надо проверить что у вотчера есть права на просмотр комментария
          if ((eventTypeId.equals(EventType.ISSUE_COMMENTED_ID) || eventTypeId.equals(EventType.ISSUE_COMMENT_EDITED_ID)) && !commentPermissionManager.hasBrowsePermission(s, commentManager.getLastComment(issue))) {
             System.out.println("TELEGRAM WATCHER ID: Not send, user dont have browse comment permission: " + s.getUsername());
          } else {
             telegramChatid.append(watcherTelegramChatid + " ");
          }
        }
      } catch (Exception e) {
        System.out.println("TELEGRAM WATCHER ID: " + e);
      }
    }

    // Шлем назначенному
    if (issue.getAssignee() != null) {
      messageToTelegram += "Исполнитель: <b>" + issue.getAssignee().getDisplayName() + "</b>\n";
      try {
        String assigneeTelegramChatid = userPropertyManager.getPropertySet(issue.getAssignee()).getString("jira.meta.chat");
        System.out.println("TELEGRAM ASSIGNEE ID: " + assigneeTelegramChatid + " USER CREATED EVENT ID: " + userCreatedEventChatID);
        if (assigneeTelegramChatid.matches("-?[0-9]+") && !assigneeTelegramChatid.equals(userCreatedEventChatID)) {
          if ((eventTypeId.equals(EventType.ISSUE_COMMENTED_ID) || eventTypeId.equals(EventType.ISSUE_COMMENT_EDITED_ID)) && !commentPermissionManager.hasBrowsePermission(issue.getAssignee(), commentManager.getLastComment(issue))) {
             System.out.println("TELEGRAM WATCHER ID: Not send, user dont have browse comment permission: " + issue.getAssignee().getUsername());
          } else {
             telegramChatid.append(assigneeTelegramChatid + " ");
          }
        }
      } catch (Exception e) {
        System.out.println("TELEGRAM ASSIGNEE ID: " + e);
      }
    }

    //System.out.println(messageToTelegram);
    System.out.println("TELEGRAM IDS: " + telegramChatid.toString() + "ISSUE: " + issue.getKey() + " " + eventType + " USER: " + issueEvent.getUser().getUsername());

    if (telegramChatid != null) {
      sendTelegm(telegramChatid.toString(), messageToTelegram);
    }
  }	

  @EventListener
  public void onMentionIssueEvent(MentionIssueEvent mentionIssueEvent) {
    Issue issue = mentionIssueEvent.getIssue();
    WatcherManager watcherManager = new ComponentAccessor().getWatcherManager();
    List<ApplicationUser> watchers = watcherManager.getWatchers(issue, en);
    StringBuilder telegramChatid = new StringBuilder();
    UserPropertyManager userPropertyManager = new ComponentAccessor().getUserPropertyManager();

    String issueAssignee = "000000";

    try {
      issueAssignee = issue.getAssignee().getKey();
    } catch (Exception e) {
      System.out.println("TELEGRAM ISSUE ASSIGNEE NULL: " + e);
    }

    String messageToTelegram = "Проект: <a href=\"https://JIRAURL.CHANGEIT/projects/" + issue.getProjectObject().getKey() + "/issues/\">[" + issue.getProjectObject().getKey() + "] " + issue.getProjectObject().getName() + "</a>\nЗадача: <a href=\"https://JIRAURL.CHANGEIT/browse/" + issue.getKey() + "\">[" + issue.getKey() + "] " + escapeMessage(issue.getSummary()) + "</a>\n";

    messageToTelegram += "Автор: <b>" + mentionIssueEvent.getFromUser().getDisplayName() + "</b>\nТип действия: <b>Упоминание в комментарии</b>\n" + "Комментарий: " + escapeMessage(mentionIssueEvent.getMentionText()) + "\n";

    for (ApplicationUser userToMention : mentionIssueEvent.getToUsers()) {
      String userToMentionTelegramChatid = userPropertyManager.getPropertySet(userToMention).getString("jira.meta.chat");
      try {
        if (userToMentionTelegramChatid.matches("-?[0-9]+") && !watcherManager.isWatching(userToMention, issue) && !userToMention.getKey().equals(issueAssignee)) {
          telegramChatid.append(userToMentionTelegramChatid + " ");
        }
        System.out.println("TELEGRAM MENTION ID: " + userToMentionTelegramChatid);
      } catch (Exception e) {
        System.out.println("TELEGRAM MENTION ID: " + e);
      }
    }

    if (telegramChatid != null) {
      sendTelegm(telegramChatid.toString(), messageToTelegram);
    }
  }

  public static String escapeMessage(String messageToEscape){
    String messageEscaped = messageToEscape.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("[", "").replace("]", "").replace("&lt;pre&gt;", "<code>").replace("&lt;/pre&gt;", "</code>");
    return messageEscaped;
  }

  public static String sendTelegm(String telegramChatid, String telegramMessage){

    List<String> cmds = new ArrayList<String>();
    cmds.add("/usr/local/bin/jiratelegram.py");
    cmds.add("-c");
    cmds.add(telegramChatid);
    cmds.add("-m");
    cmds.add(telegramMessage);

    ProcessBuilder bld = new ProcessBuilder(cmds);
    BufferedReader br = null;
    Process p = null;
    String result = null;
    try {
      p = bld.start();
      InputStream is = p.getInputStream();
      InputStreamReader isr = new InputStreamReader(is);
      br = new BufferedReader(isr);
      String line;

      while ((line = br.readLine()) != null) {
        System.out.println(line);
      }

    } catch (Exception e) {
      System.out.println(e);
    } finally {
      if(br != null){
        try{ br.close(); }catch(IOException e){}
      }
      if(p != null){
        p.destroy();
      }
    }
    return result;
  }

}

