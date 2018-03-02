/*
 * Copyright 2012 LinkedIn Corp.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package azkaban.utils;

import static java.util.Objects.requireNonNull;

import azkaban.Constants;
import azkaban.Constants.ConfigurationKeys;
//import org.apache.commons.beanutils.PropertyUtils;
import azkaban.alert.Alerter;
import azkaban.executor.ExecutableFlow;
import azkaban.executor.ExecutableNode;
import azkaban.executor.ExecutionOptions;
import azkaban.executor.Status;
import azkaban.executor.mail.DefaultMailCreator;
import azkaban.executor.mail.MailCreator;
import azkaban.metrics.CommonMetrics;
import azkaban.sla.SlaOption;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.*;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.log4j.Logger;

@Singleton
public class Emailer extends AbstractMailer implements Alerter {

  private static final String HTTPS = "https";
  private static final String HTTP = "http";
  private static final Logger logger = Logger.getLogger(Emailer.class);
  private final CommonMetrics commonMetrics;
  private final String scheme;
  private final String clientHostname;
  private final String clientPortNumber;
  private final String mailHost;
  private final int mailPort;
  private final String mailUser;
  private final String mailPassword;
  private final String mailSender;
  private final String azkabanName;
  private final String tls;
  private boolean testMode = false;
  private final String urlmsg_url;
  private final String urlmsg_props;
  private final String urlmsg_user_flag;
  private final String urlmsg_msg_flag;
  private final String urlmsg_type;
  private final boolean url_wechat;
  private String urlmsg_user;
  @Inject
  public Emailer(final Props props, final CommonMetrics commonMetrics) {
    super(props);
    this.commonMetrics = requireNonNull(commonMetrics, "commonMetrics is null.");
    this.azkabanName = props.getString("azkaban.name", "azkaban");
    this.mailHost = props.getString("mail.host", "localhost");
    this.mailPort = props.getInt("mail.port", DEFAULT_SMTP_PORT);
    this.mailUser = props.getString("mail.user", "");
    this.mailPassword = props.getString("mail.password", "");
    this.mailSender = props.getString("mail.sender", "");
    this.tls = props.getString("mail.tls", "false");

    this.urlmsg_url=props.getString("urlmsg.host", "http://localhost");
    this.urlmsg_props=props.getString("urlmsg.props", "");
    this.urlmsg_user_flag=props.getString("urlmsg.user_flag", "");
    this.urlmsg_msg_flag=props.getString("urlmsg.msg_flag", "");
    this.urlmsg_type=props.getString("urlmsg.type", "post");
    this.url_wechat=props.getBoolean("urlmsg.wechat", false);
    if (urlmsg_user_flag.length() >0){
      urlmsg_user=props.getString(urlmsg_user_flag,"");
    }

    final int mailTimeout = props.getInt("mail.timeout.millis", 30000);
    EmailMessage.setTimeout(mailTimeout);
    final int connectionTimeout =
        props.getInt("mail.connection.timeout.millis", 30000);
    EmailMessage.setConnectionTimeout(connectionTimeout);

    EmailMessage.setTotalAttachmentMaxSize(getAttachmentMaxSize());

    this.clientHostname = props.getString(ConfigurationKeys.AZKABAN_WEBSERVER_EXTERNAL_HOSTNAME,
        props.getString("jetty.hostname", "localhost"));

    if (props.getBoolean("jetty.use.ssl", true)) {
      this.scheme = HTTPS;
      this.clientPortNumber = Integer.toString(props
          .getInt(ConfigurationKeys.AZKABAN_WEBSERVER_EXTERNAL_SSL_PORT,
              props.getInt("jetty.ssl.port",
                  Constants.DEFAULT_SSL_PORT_NUMBER)));
    } else {
      this.scheme = HTTP;
      this.clientPortNumber = Integer.toString(
          props.getInt(ConfigurationKeys.AZKABAN_WEBSERVER_EXTERNAL_PORT, props.getInt("jetty.port",
              Constants.DEFAULT_PORT_NUMBER)));
    }

    this.testMode = props.getBoolean("test.mode", false);
  }

  public static List<String> findFailedJobs(final ExecutableFlow flow) {
    final ArrayList<String> failedJobs = new ArrayList<>();
    for (final ExecutableNode node : flow.getExecutableNodes()) {
      if (node.getStatus() == Status.FAILED) {
        failedJobs.add(node.getId());
      }
    }
    return failedJobs;
  }

  private void sendSlaAlertEmail(final SlaOption slaOption, final String slaMessage) {
    final String subject =
        "SLA violation for " + getJobOrFlowName(slaOption) + " on " + getAzkabanName();
    final List<String> emailList =
        (List<String>) slaOption.getInfo().get(SlaOption.INFO_EMAIL_LIST);
    logger.info("Sending SLA email " + slaMessage);
    sendEmail(emailList, subject, slaMessage);
  }

  /**
   * Send an email to the specified email list
   */
  public void sendEmail(final List<String> emailList, final String subject, final String body) {
    if (emailList != null && !emailList.isEmpty()) {
      final EmailMessage message =
          super.createEmailMessage(subject, "text/html", emailList);

      message.setBody(body);

      if (!this.testMode) {
        try {
          message.sendEmail();
          logger.info("Sent email message " + body);
          this.commonMetrics.markSendEmailSuccess();
        } catch (final Exception e) {
          logger.error("Failed to send email message " + body, e);
          this.commonMetrics.markSendEmailFail();
        }
      }
    }
  }

  private String getJobOrFlowName(final SlaOption slaOption) {
    final String flowName = (String) slaOption.getInfo().get(SlaOption.INFO_FLOW_NAME);
    final String jobName = (String) slaOption.getInfo().get(SlaOption.INFO_JOB_NAME);
    if (org.apache.commons.lang.StringUtils.isNotBlank(jobName)) {
      return flowName + ":" + jobName;
    } else {
      return flowName;
    }
  }

  private  void   sendurl(final ExecutableFlow flow) throws UnsupportedEncodingException {
    SimpleDateFormat utcFormater = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    utcFormater.setTimeZone(TimeZone.getTimeZone("Asia/Shanghai"));
    Map<String ,String> pops= new HashMap<>();
    String str_pop="";
    String jsn_pop="";
    Date t_d= new Date();
    if (flow.getEndTime() >0) {
      t_d.setTime(flow.getEndTime());
    }
    String s_msg=String.format("project name : %s at %s has failed",flow.getProjectName(),utcFormater.format(t_d));

    if (urlmsg_url.length() >0  && urlmsg_props.length() >0) {
        String tmp_str[]=urlmsg_props.split(";");

        for (String m:tmp_str){
            pops.put(m.split(":",-1)[0],m.split(":",-1)[1]);
            str_pop+=m.split(":",-1)[0]+"="+m.split(":",-1)[1]+"&";
            jsn_pop+="\""+m.split(":",-1)[0]+"\":"+"\""+m.split(":",-1)[1]+"\",";
        }

        if (urlmsg_user_flag.length() > 0 && urlmsg_user.length() > 0) {
            jsn_pop += "\"" + urlmsg_user_flag + "\":\"" + urlmsg_user + "\",";
            str_pop +=urlmsg_user_flag+"="+urlmsg_user+ "&";
        }

        if (urlmsg_msg_flag.length() > 0) {
            jsn_pop += "\"" + urlmsg_msg_flag + "\":\"" + s_msg + "\",";
            str_pop +=urlmsg_user_flag+"="+urlmsg_user+ "&";
        }

        if (str_pop.charAt(str_pop.length()-1) == '&'){
            str_pop=str_pop.substring(0,str_pop.length()-1);
        }
        if (jsn_pop.charAt(jsn_pop.length()-1) == '&'){
            jsn_pop=jsn_pop.substring(0,jsn_pop.length()-1);
        }

        String url = new String(urlmsg_url);
        if (url.indexOf(url.length() - 1) == '/') {
            url = url.substring(0, url.length() - 1);
        }
        if (url_wechat) {
            if (pops.size() > 0) {
                UrlMessage.alertWeixin(url,pops,s_msg);
            }
        } else {
            if (urlmsg_type.toLowerCase().equals("post")) {
                //post 模式(json 的字符串格式)
                 UrlMessage.sendPost(url, "{" + jsn_pop + "}");
            } else {
                //get 模式 &模式
                UrlMessage.sendGet(urlmsg_url,str_pop);
            }
        }
    }
  }

  public void sendFirstErrorMessage(final ExecutableFlow flow) {
      if (flow.getExecutionOptions().isMailForurl()){
          try {
            sendurl(flow);
          } catch (final Exception e) {
              logger.error(
                      "Failed to send first error url message for execution " + flow.getExecutionId(), e);
              this.commonMetrics.markSendEmailFail();
          }
          return;
      }
    final EmailMessage message = new EmailMessage(this.mailHost, this.mailPort, this.mailUser,
        this.mailPassword);
    message.setFromAddress(this.mailSender);
    message.setTLS(this.tls);
    message.setAuth(super.hasMailAuth());

    final ExecutionOptions option = flow.getExecutionOptions();
    //todo 这里判断执行邮件还是url massage
    final MailCreator mailCreator =
        DefaultMailCreator.getCreator(option.getMailCreator());

    logger.debug("ExecutorMailer using mail creator:"
        + mailCreator.getClass().getCanonicalName());

    final boolean mailCreated =
        mailCreator.createFirstErrorMessage(flow, message, this.azkabanName, this.scheme,
            this.clientHostname, this.clientPortNumber);

    if (mailCreated && !this.testMode) {
      try {
        message.sendEmail();
        logger.info("Sent first error email message for execution " + flow.getExecutionId());
        this.commonMetrics.markSendEmailSuccess();
      } catch (final Exception e) {
        logger.error(
            "Failed to send first error email message for execution " + flow.getExecutionId(), e);
        this.commonMetrics.markSendEmailFail();
      }
    }
  }

  public void sendErrorEmail(final ExecutableFlow flow, final String... extraReasons) {
      if (flow.getExecutionOptions().isMailForurl()){
          try {
              sendurl(flow);
          } catch (final Exception e) {
              logger.error(
                      "Failed to send first error url message for execution " + flow.getExecutionId(), e);
              this.commonMetrics.markSendEmailFail();
          }
          return;
      }
    final EmailMessage message = new EmailMessage(this.mailHost, this.mailPort, this.mailUser,
        this.mailPassword);
    message.setFromAddress(this.mailSender);
    message.setTLS(this.tls);
    message.setAuth(super.hasMailAuth());

    final ExecutionOptions option = flow.getExecutionOptions();
    //todo 这里判断执行邮件还是url massage
    final MailCreator mailCreator =
        DefaultMailCreator.getCreator(option.getMailCreator());
    logger.debug("ExecutorMailer using mail creator:"
        + mailCreator.getClass().getCanonicalName());

    final boolean mailCreated =
        mailCreator.createErrorEmail(flow, message, this.azkabanName, this.scheme,
            this.clientHostname, this.clientPortNumber, extraReasons);

    if (mailCreated && !this.testMode) {
      try {
        message.sendEmail();
        logger.info("Sent error email message for execution " + flow.getExecutionId());
        this.commonMetrics.markSendEmailSuccess();
      } catch (final Exception e) {
        logger
            .error("Failed to send error email message for execution " + flow.getExecutionId(), e);
        this.commonMetrics.markSendEmailFail();
      }
    }
  }

  public void sendSuccessEmail(final ExecutableFlow flow) {
      if (flow.getExecutionOptions().isMailForurl()){
          try {
              sendurl(flow);
          } catch (final Exception e) {
              logger.error(
                      "Failed to send first error url message for execution " + flow.getExecutionId(), e);
              this.commonMetrics.markSendEmailFail();
          }
          return;
      }
    final EmailMessage message = new EmailMessage(this.mailHost, this.mailPort, this.mailUser,
        this.mailPassword);
    message.setFromAddress(this.mailSender);
    message.setTLS(this.tls);
    message.setAuth(super.hasMailAuth());

    final ExecutionOptions option = flow.getExecutionOptions();
   //todo 这里判断执行邮件还是url massage
    final MailCreator mailCreator =
        DefaultMailCreator.getCreator(option.getMailCreator());
    logger.debug("ExecutorMailer using mail creator:"
        + mailCreator.getClass().getCanonicalName());

    final boolean mailCreated =
        mailCreator.createSuccessEmail(flow, message, this.azkabanName, this.scheme,
            this.clientHostname, this.clientPortNumber);

    if (mailCreated && !this.testMode) {
      try {
        message.sendEmail();
        logger.info("Sent success email message for execution " + flow.getExecutionId());
        this.commonMetrics.markSendEmailSuccess();
      } catch (final Exception e) {
        logger.error("Failed to send success email message for execution " + flow.getExecutionId(),
            e);
        this.commonMetrics.markSendEmailFail();
      }
    }
  }

  @Override
  public void alertOnSuccess(final ExecutableFlow exflow) throws Exception {
    sendSuccessEmail(exflow);
  }

  @Override
  public void alertOnError(final ExecutableFlow exflow, final String... extraReasons)
      throws Exception {
    sendErrorEmail(exflow, extraReasons);
  }

  @Override
  public void alertOnFirstError(final ExecutableFlow exflow) throws Exception {
    sendFirstErrorMessage(exflow);
  }

  @Override
  public void alertOnSla(final SlaOption slaOption, final String slaMessage)
      throws Exception {
    sendSlaAlertEmail(slaOption, slaMessage);
  }
}
