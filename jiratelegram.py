#!/usr/bin/env python3

import requests
import json
import argparse

proxies = {'http': 'socks5h://user:pass@1.1.1.1:666', 'https': 'socks5h://user:pass@1.1.1.1:666'}
parser = argparse.ArgumentParser()
parser.add_argument("-c", "--chatid", dest="chatid", default="0000000", help="Chat id for telegram, default: mine")
parser.add_argument("-m", "--message", dest="message", default="Error, message is empty from jira", help="Message text, default: error")
args = parser.parse_args()

chat_id = args.chatid
message = args.message

URL = 'https://api.telegram.org/bot'
TOKEN = '111111111:AAAAAAAAAAAAAAAAAAAAAAAAAA'

chat_ids = chat_id.strip().split()
chat_ids = list(set(chat_ids))

for chat in chat_ids:
  data = {'disable_web_page_preview': 1, 'chat_id': chat, 'text': message, 'parse_mode': 'HTML'}
  try:
    url = URL + TOKEN + '/sendMessage'
    request = requests.post(url, proxies=proxies, data=data, timeout=3)
    print('TELEGRAM', request.status_code, request.reason, chat)
  except:
    print('TELEGRAM Error getting updates', chat)

