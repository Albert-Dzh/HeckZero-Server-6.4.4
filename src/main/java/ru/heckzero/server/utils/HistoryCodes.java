package ru.heckzero.server.utils;

public class HistoryCodes {
	public static final int LOG_LOGIN = 1;																							//Вход в игру с IP=%s
	public static final int LOG_LOGOUT = 2;																							//Выход из игры
	public static final int LOG_WRONG_PASSWORD = 3;																					//Был введен неверный пароль с IP=%s
	public static final int LOG_CHANGE_PASSWORD_IP = 4;																				//Изменен пароль с IP
	public static final int LOG_CHANGE_EMAIL_IP = 5;																				//Изменен email с IP
	public static final int LOG_EKEY_ACTIVATED = 6;																					//Активирован электронный ключ. IP=%s
	public static final int LOG_EKEY_DEACTIVATED = 7;																				//Деактивирован электронный ключ. IP=%s
	public static final int LOG_LOGIN_FROM_IP_VC = 8;																				//Вход в игру с IP=%s, клиент v%s
	public static final int LOG_UNAVAILABLE = 9;																					//В данный момент логи недоступны, повторите запрос позднее
	public static final int LOG_BALANCE_INFO = 10;																					//На счету %s медных монет и %s серебряных.
	public static final int LOG_WRONG_MANY_TIMES = 11;																				//C IP адреса {%s} был более 5 раз введен неправильный пароль к вашему персонажу
	
	public static final int LOG_WIRE = 100;																							//Сообщение от \'%s\': {%s}"
	public static final int LOG_COP_BLOCKED_CHAT= 105;																				//Полицейский '%s' запретил вам общение в чате на %s мин 
	public static final int LOG_COP_BLOCKED_FORUM = 106;																			//Полицейский '%s' запретил вам общение на форуме на %s мин 
	public static final int LOG_COP_BLOCKED_YOU = 107;																				//Полицейский '%s' заблокировал вашего персонажа
	public static final int LOG_COP_UNBLOCKED_CHAT = 108;																			//Полицейский '%s' снял запрет на общение в чате 
	public static final int LOG_COP_UNBLOCKED_FORUM = 109;																			//Полицейский '%s' снял запрет на общение на форуме 
	public static final int LOG_COP_SEARCH_YOU = 111;																				//Вас обыскал полицейский '%s' 
	public static final int LOG_GIFT_DELIVERED_FROM_PLAYER = 118;																	//Вам доставлен подарок от персонажа '%s' 
	public static final int LOG_PLAYER_BUY_YOUR_ITEM = 121;																			//Персонаж '%s' купил ваш предмет {%s} в комиссионном магазине '%s'. Ваш доход {%s} мнт.
	public static final int LOG_GIFT_MADE_FOR_YOU = 122;																			//Персонаж '%s' приготовил для вас подарок,  вы можете получить его в здании: %s 
	public static final int LOG_STORE_OWNER_REMOVED_YOUR_ITEM = 133;																//Владелец магазина '%s' убрал с витрины ваш товар '%s'
	public static final int LOG_STORE_OWNER_PUT_YOUR_ITEM = 134;																	//Владелец магазина '%s' выложил на витрину ваш товар '%s'
	public static final int LOG_BUY_GIFT_FOR_PLAYER = 136;																			//Приобретено '%s' для персонажа '%s' с доставкой через %s часов 
	public static final int LOG_PARCEL_ARRIVED = 138;																				//Вам пришла посылка от '%s', получить можно в любом почтовом отделении!
	public static final int LOG_PUT_RES_TO_FP = 141;																				//Сдано {%s} на общественный завод '%s'
	public static final int LOG_PERK_TAKEN = 142;																					//Взят перк '%s' 
	public static final int LOG_PERK_DROPPED = 143;																					//Удален перк '%s'. Затрачено '%s'. В рюкзаке осталось %s мнт. 
	public static final int LOG_COP_BLOCKED_ACCOUNT = 144;																			//Полицейский '%s' заблокировал ваш счёт. Причина: %s 
	public static final int LOG_COP_UNBLOCKED_ACCOUNT = 145;																		//Полицейский '%s' разблокировал ваш счёт. Причина: %s 
	public static final int LOG_STORE_OWNER_SHIFTED_YOUR_ITEM = 156;																//Владелец магазина '%s' переложил ваш товар {%s} на склад 
	public static final int LOG_CITIZEN_CANCELED_BY_MAYOR = 159;																	//Мэр %s лишил вас граждантсва %s
	public static final int LOG_CLAN_POLL_CREATED = 165;																			//Создано голосование за кандидата %s. 
	public static final int LOG_CLAN_CHANGE_LEADER = 166;																			//Передано главенство клана персонажу %s
	public static final int LOG_CLAN_JOIN_REQ = 167;																				//Подана заявка на вступление в клан %s 
	public static final int LOG_GET_ITEMS_FROM = 200;																				//Получено: {%s} от '%s'
	public static final int LOG_GET_ITEMS_IN_HOUSE = 201;																			//Получены предметы: {%s} в здании \'%s\' 
	public static final int LOG_CURRENCY_EXCHANGE = 203;																			//Обмен валюты: {%s[%s]} ->; {%s[%s]}
	public static final int LOG_RECEVIED_FROM_PAYER = 204;																			//Получены {%s} %s от персонажа '%s'
	public static final int LOG_GIFT_FOR_YOU_FROM = 205;																			//Вам подарено: {%s} персонажем '%s' 
	public static final int LOG_RECEIVE_ITEMS = 206;																				//Почтой получены предметы: {%s} от персонажа \'%s\'
	public static final int LOG_SALE_AND_GET_MONEY = 208;																			//Продано {%s} в '%s', получено %s мнт.
	public static final int LOG_BUY_IN_SHOP_AND_BALANCE = 209;																		//В магазине '%s' приобретено {%s} на сумму %s мнт. В рюкзаке осталось %s мнт 
	public static final int LOG_GET_ITEMS_FROM_DEBT = 215;																			//Получено в долг: {%s} от '%s'
	public static final int LOG_MONEY_TRANSFER_FROM = 217;																			//Получено {%s[%s]} от персонажа \'%s\'. Всего на счету: %s мнт. %s",

	public static final int LOG_GIVE_ITEMS_TO = 300;																				//Передал: {%s} к \'%s\'
	public static final int LOG_PUT_ITEMS_IN_HOUSE = 301;																			//Отданы предметы: {%s} в здании \'%s\'
	public static final int LOG_DROP_ITEMS_IN_BATTLE = 302;																			//Выброшено: {%s} в бою \'%s\'
	public static final int LOG_DROP_ITEMS = 303;																					//Выброшено: {%s} 
	public static final int LOG_PAY = 304;																							//Оплатил {%s} в \'%s\' %s
	public static final int LOG_ITEM_EXPIRED = 306;																					//Истек срок действия предмета: {%s}
	public static final int LOG_GAVE_TO_PLAYER = 308;																				//Передал {%s} %s персонажу '%s'
	public static final int LOG_GIFT_PACKAGED = 311;																				//Упаковано: {%s} в подарок персонажу '%s' 
	public static final int LOG_SEND_ITEMS = 312;																					//Переслал: {%s} персонажу \'%s\'
	public static final int LOG_USE_FOR_SUBWAY = 316;																				//Использовал {%s} для прохода в разрушенное метро 
	public static final int LOG_PAY_AND_BALANCE = 318;																				//Оплатил {%s} в \'%s\' %s. В рюкзаке осталось %s мнт.
	public static final int LOG_PUT_ITEMS_AND_BALANCE = 320;																		//Отданы предметы: {%s} в здании '%s'. В рюкзаке осталось %s мнт.
	public static final int LOG_USE_BY_MODULE = 321;																				//Использовано {%s} модулем {%s} 
	public static final int LOG_USE_FOR_MANUFACTURE = 322;																			//Использовано {%s} для изготовления {%s} 
	public static final int LOG_GIVE_ITENS_TO_DEBT = 324;																			//Передал в долг: {%s} к '%s'
	public static final int LOG_MONEY_TRANSFER_TO = 326;																			//Перевёл {%s[%s]} персонажу \'%s\'. Стоимость отправки %s мнт. Осталось %s мнт. %s"
	public static final int LOG_CLAN_BUY_AND_BALANCE = 334;																			//Оплачено %s за приобретение клана %s. Сумма на счету %s. 
	public static final int LOG_DROP_ITEMS_IN_LOCATION = 344;																		//Выброшено: {%s} в локации %s.

	public static final int LOG_CLAN_JOINED = 402;																					//Вас приняли в клан '%s'
	public static final int LOG_CLAN_LEFT = 403;																					//Вы покинули клан '%s'
	public static final int LOG_CLAN_STATUS_CHANGE = 404;																			//Изменен статус в клане. Отдел: '%s'. Должность: '%s'. 
	public static final int LOG_CLAN_PLAYER_CHANGE_STATUS = 405;																	//Персонаж '%s' изменил статус в клане. Отдел: '%s'. Должность: '%s'. 

	public static final int LOG_PAY_FOR_CANCEL_CITIZENSHIP = 328;																	//Заплачено %s монет за лишение гражданства персонажа %s"
	public static final int LOG_GET_MONEY_FROM_CASH	= 700;																			//Забрал {%s[%s]} из кассы %s. В рюказке стало %s мнт.
	public static final int LOG_PUT_MONEY_TO_CASH = 701;																			//Положил {%s[%s]} в кассу %s. В рюказке осталось %s мнт.
	
																//******************some common building logs****************
	public static final int LOG_BLD_PLAYER_START_REPAIR = 1100;																		//Персонаж '%s' запустил процесс ремонта здания 
	public static final int LOG_BLD_REPAIR_FINISHED = 1101;																			//Окончен ремонт. Состояние здания %s. 
	public static final int LOG_BLD_PLAYER_CANCELED_REPAIR = 1102;																	//Персонаж '%s' остановил ремонт. Состояние здания %s. 
	public static final int LOG_BLD_PLAYER_TAKE_MONEY_FROM_CASH = 1200;																//Персонаж '%s' забрал из кассы здания {%s[%s]}. Всего в кассе задния: %s мнт. 
	public static final int LOG_BLD_PLAYER_PUT_MONEY_TO_CASH = 1201;																//Персонаж '%s' положил в кассу здания {%s[%s]}. Всего в кассе задния: %s мнт. 
	public static final int LOG_BLD_PLAYER_PUT_FOR_REPAIR = 1300;																	//Персонаж '%s' сдал {%s} на ремонт здания 
	public static final int LOG_BLD_PLAYER_INCREASE_CAPACITY = 1500;																//Персонаж '%s' увеличил вместимость здания. Стоимость постройки: %s мнт. 

																//******************Bank cell log codes**********************
	public static final int LOG_CELL_RENT_RENEW = 904100;																			//Персонаж '%s' продлил аренду ячейки до %s
	public static final int LOG_CELL_MARKED_DEALER = 904101;																		//Эта ячейка помечена как дилерская 
	public static final int LOG_CELL_MARKED_NOT_DEALER = 904102;																	//Снят статус дилерской ячейки 
	public static final int LOG_CELL_BLOCKED_BY = 904103;																			//Ячейка заблокирована персонажем '%s'. Причина: '%s' 
	public static final int LOG_CELL_UNBLOCKED_BY = 904104;																			//Ячейка разблокирована персонажем '%s' 
	public static final int LOG_CELL_WAS_BOUGHT_BY = 904105;																		//Персонаж '%s' купил эту ячейку 
	public static final int LOG_CELL_KEY_DUBLICATE = 904106;																		//Персонаж '%s' изготовил дубликат ключа от ячейки 
	public static final int LOG_CELL_PSWD_CHANGE = 904107;																			//Персонаж '%s' сменил пароль от ячейки 
	public static final int LOG_CELL_COP_VIEW = 904108;																				//Полицейский '%s' просмотрел логи/содержимое ячейки 
	public static final int LOG_CELL_PUT_ITEMS = 904200;																			//Персонаж '%s' положил в ячейку предметы: {%s} 
	public static final int LOG_CELL_ITEMS_TRANFERED_TO_THIS_CELL = 904201;															//Персонаж '%s' перевел со счета {%s} на ваш счет %s на сумму: {%s} 
	public static final int LOG_CELL_ADMINS_TRANSFERRED_SHOTA_TO_THIS_CELL = 904202;												//Администрация перевела на ваш счет %s на сумму: {%s} 
	public static final int LOG_CELL_PUT_COINS = 904203;																			//Персонаж '%s' положил в ячейку монеты {%s}, сумма на счету: %s 
	public static final int LOG_CELL_ITEMS_TRANSFERED_TO_THIS_CELL_AND_BALANCE = 904204;											//Персонаж '%s' перевел со счета {%s} на ваш счет %s на сумму: {%s}, сумма на счету: %s 
	public static final int LOG_CELL_RES_TRANSFERED_FROM = 904205;																	//Персонаж '%s' перевёл со счёта {%s} ресурсы: {%s} 
	public static final int LOG_CELL_GET_ITEMS  = 904300;																			//Персонаж '%s' забрал из ячейки предметы: {%s} 
	public static final int LOG_CELL_TRANSFERED_TO_ACCOUNT_COST = 904301;															//Персонаж '%s' перевел на счет {%s} %s на сумму: {%s} 
	public static final int LOG_CELL_ADMINS_REMOVED_SHOTA_IN_AMOUNT_OF = 904302;													//Администрация списала с вашего счета %s на сумму: {%s} 
	public static final int LOG_CELL_TRANSFERRED_BY_POST = 904303;																	//'%s' перевел почтой серебряные монеты на сумму: {%s} персонажу '%s' 
	public static final int LOG_CELL_GET_COINS_FROM_THIS_CELL = 904304;																//Персонаж '%s' забрал из ячейки монеты {%s}, сумма на счету: %s" 
	public static final int LOG_CELL_ITEMS_TRANSFERED_FROM_THIS_CELL_AND_BALANCE = 904305;											//Персонаж '%s' перевел на счет {%s} %s на сумму: {%s}, сумма на счету: %s 
	public static final int LOG_CELL_TRANSFERRED_SILVER_BY_POST_AND_BALANCE = 904306;												//'%s' перевел почтой серебряные монеты на сумму: {%s} персонажу '%s', сумма на счету: %s 
	public static final int LOG_CELL_COP_TAKE_SHOTA = 904307;																		//Полицейский '%s' конфисковал {%s} 
	public static final int LOG_CELL_RES_TRANSFERED_TO = 904308;																	//Персонаж '%s' перевёл на счет {%s} ресурсы: {%s} 

															//******************Bank log codes**********************
	public static final int LOG_BANK_CHANGE_PARAMS = 4100;																			//Персонаж '%s' изменил настройки 
	public static final int LOG_BANK_PROFIT_FOR_CELL = 4200;																		//Доход банка %s мнт. за продажу ячейки 
	public static final int LOG_BANK_PROFIT_FOR_CELL_RENT_RENEW = 4201;																//Доход банка %s мнт. за продление ячейки 
	public static final int LOG_BANK_BUY_PAPER = 4202;																				//Персонаж '%s' купил вексель на сумму %s мнт. 
	public static final int LOG_BANK_RECEIVE_FROM_EXCHANGE_POINT = 4203;															//Пришло с обменного пункта {%s %s}
	public static final int LOG_BANK_PUT_MONEY_TO_CASH = 4204;																		//Владелец банка '%s' положил в кассу %s мнт. 
	public static final int LOG_BANK_GET_MONEY_FROM_CASH = 4300;																	//Владелец банка '%s' забрал из кассы %s мнт. 
	public static final int LOG_BANK_CASH_PAPER = 4301;																				//Персонаж '%s' обналичил вексель на сумму %s мнт. 
	public static final int LOG_BANK_SEND_TO_EXCHANGE_POINT = 4302;																	//Ушло в обменный пункт {%s %s} 

															//******************Clan Office log codes**********************
	public static final int LOG_OF_PLAYER_JOIN_CLAN = 8101;																			//Персонаж '%s' принят в клан 
	public static final int LOG_OF_PLAYER_LEAVE_CLAN = 8102;																		//Персонаж '%s' изгнан из клана 
	public static final int LOG_OF_ISSUE_TEMP_PASS = 8103;																			//Персонаж '%s' сделал временный пропуск 
	public static final int LOG_OF_RENEW_RENT = 8104;																				//Персонаж '%s' продлил аренду здания до %s 
	public static final int LOG_OF_PLAYER_PUT_ITEM = 8200;																			//Персонаж '%s' выложил {%s} в арсенал %s 
	public static final int LOG_OF_PLAYER_PAY_TAX = 8201;																			//Персонаж '%s' заплатил налоги: %s мнт. 
	public static final int LOG_OF_PLAYER_GET_ITEM = 8300;																			//Персонаж '%s' забрал {%s} из арсенала %s 

	
															//******************Post Office log codes**********************
	public static final int LOG_POST_CHANGE_PARAMS = 9100;																			//Персонаж '%s' изменил настройки
	public static final int LOG_POST_PAY_FOR_WIRE = 9200;																			//Персонаж '%s' заплатил %s мнт. за отправку телеграммы
	public static final int LOG_POST_PAY_FOR_PARCEL = 9201;																			//Персонаж '%s' заплатил %s мнт. за отправку посылки 
	public static final int LOG_POST_GET_MONEY = 9300;																				//Владелец почты '%s' забрал из кассы %s мнт. 

															//******************Portals log codes**********************
	public static final int LOG_PORTALS_CHANGE_PARAMS = 11100;																		//Персонаж '%s' изменил настройки 
	public static final int LOG_PORTAL_PAY_FOR_FLIGHT = 11200;																		//Персонаж '%s' заплатил %s мнт. за телепортацию 
	public static final int LOG_PORTAL_PAY_FOR_FLIGHT_AND_RES_USED = 11201;															//Персонаж '%s' заплатил %s мнт. за телепортацию, израсходованы ресурсы: %s 
	public static final int LOG_PORTAL_GET_MONEY = 11300;																			//Владелец портала '%s' забрал из кассы %s мнт. 


															//********************Hospital log codes*************************
	public static final int LOG_HL_PLAYER_CHANGE_PARAM = 5100;																		//Владелец больницы '%s' изменил настройки 
	public static final int LOG_HL_PLAYER_PAYED_FOR_HEAL = 5200;																	//Персонаж '%s' заплатил за лечение %s мнт. 
	public static final int LOG_HL_PLAYER_PAYED_FOR_STAT = 5201;																	//Персонаж '%s' заплатил за перераспределение параметров %s мнт. 
	public static final int LOG_HL_PLAYER_TAKE_CASH = 5300;																			//Владелец больницы '%s' забрал из кассы %s мнт. 

	
															//******************city hall log codes**********************
	public static final int LOG_CITY_HALL_CHANGE_PARAMS = 7100;																		//Персонаж '%s' изменил настройки 
	public static final int LOG_CITY_HALL_RENAME_BUILDING = 7101;																	//Персонаж '%s' переименовал здание '%s' в '%s' 
	public static final int LOG_CITY_HALL_PAY_TAX = 7200;																			//Персонаж '%s' заплатил налоги: %s мнт." 
	public static final int LOG_CITY_HALL_BUY_LIC = 7201;																			//Персонаж '%s' купил лицензию {%s} за %s мнт." 
	public static final int LOG_CITY_HALL_BUY_PASSPORT = 7202;																		//Персонаж '%s' купил паспорт: %s мнт. 
	public static final int LOG_CITY_HALL_REG_TRADEMARK = 7203;																		//Персонаж '%s' зарегистрировал торговую марку {%s} 
	public static final int LOG_CITY_HALL_ORDER_REPAIR_BUILDING = 7204;																//Персонаж '%s' заказал ремонт здания '%s' за %s мнт. 
	public static final int LOG_CITY_HALL_BUY_VIP_CARD = 7205;																		//Персонаж '%s' купил VIP-card: %s с.м. 
	public static final int LOG_CITY_HALL_GET_MONEY_FROM_CASH = 7300;																//Владелец мэрии '%s' забрал из кассы %s мнт. 
	public static final int LOG_CITY_HALL_CANCEL_CITIZENSHIP = 97301;																//%s лишил гражданства персонажа %s и заплатил за это %s монет." 

														//******************Shop log codes**********************
	public static final int LOG_SHOP_CHANGE_PARAMS = 3100;																			//Владелец магазина '%s' изменил настройки 
	public static final int LOG_SHOP_OWNER_SET_COST =3101;																			//Владелец магазина '%s' товару {%s} установил цену %s мнт. 
	public static final int LOG_SHOP_OWNER_PUT_ITEM_TO_SK = 3102;																	//Владелец магазина '%s' переложил товар {%s} на склад 
	public static final int LOG_SHOP_OWNER_PUT_ITEM_TO_KS = 3103;																	//Владелец магазина '%s' переложил товар {%s} на прилавок 
	public static final int LOG_SHOP_ITEM_PUTTED_TO_WH = 3104;																		//Товар {%s} перенесен на склад, т.к. очень долго лежал в комиссионке 
	public static final int LOG_SHOP_CURRENT_BUILD_STATUS = 3105;																	//Текущее состояние здания %s. Потребуется для ремонта: {%s}. Качество после ремонта будет: %s 
	public static final int LOG_SHOP_PLAYER_BUY_ITEM = 3200;																		//Персонаж '%s' купил товар {%s}, доход магазина %s мнт. 
	public static final int LOG_SHOP_PLAYER_TAKE_ITEM = 3201;																		//Персонаж '%s' забрал товар {%s}, заплатив за хранение %s мнт. 
	public static final int LOG_SHOP_OWNER_PUT_FOR_SALE = 3202;																		//Владелец магазина '%s' выложил на продажу {%s} по цене %s мнт. 
	public static final int LOG_SHOP_PLAYER_BUY_ITEM_INCLUDED = 3204;																//Персонаж '%s' купил товар {%s} (%s), доход магазина %s мнт. 
	public static final int LOG_SHOP_OWNER_ADD_MONEY_TO_CASH = 3205;																//Владелец магазина '%s' положил в кассу %s мнт. 
	public static final int LOG_SHOP_PLAYER_SALE_RES = 3300;																		//Персонаж '%s' продал в магазин ресурс {%s} по цене %s мнт. 
	public static final int LOG_SHOP_OWNER_TAKE_ITEM = 3301;																		//Владелец магазина '%s' забрал товар {%s} 
	public static final int LOG_SHOP_PPLAYER_BUY_OWNERS_ITEM = 3302;																//Персонаж '%s' купил товар владельца магазина {%s} 
	public static final int LOG_SHOP_OWNER_DROP_ITEM = 3303;																		//Владелец магазина '%s' выбросил товар {%s} 
	public static final int LOG_SHOP_OWNER_USE_RES_FROM_WH = 3304;																	//Владелец '%s' использовал для ремонта {%s} со склада здания 
	
														//*****Greenhouse log codes********
	public static final int LOG_GH_PLAYER_BUY_BF = 23200;																			//Персонаж '%s' купил букет на сумму: %s 
	public static final int LOG_GH_PLAYER_BUY_GIFT = 23201;																			//Персонаж '%s' купил подарок на сумму: %s 
	public static final int LOG_GH_PLAYER_LEAVE_GIFT = 23202;																		//Персонаж '%s' оставил в подарок для '%s': {%s} 
	public static final int LOG_GH_PLAYER_TAKE_GIFT = 23300;																		//Персонаж '%s' забрал подарок от '%s': {%s} 
	
														//*****Concentration Factory log codes********
	public static final int LOG_RF_CHANGE_PARAMS = 24100;																			//Персонаж '%s' изменил настройки 
	public static final int LOG_RF_REFINE_DONE = 24200;																				//Обогащены ресурсы %s, получено %s мнт. 
	public static final int LOG_RF_OWNER_GET_MONEY = 24300;																			//Владелец здания '%s' забрал из кассы %s мнт. 
	
														//*****Public Factory log codes********
	public static final int LOG_FP_PLAYER_PUT_RES = 36200;																			//Персонаж '%s' сдал ресурсы {%s} 
	public static final int LOG_FP_PLAYER_ORDERED_PRODUCTION  = 36300;																//Персонаж '%s' заказал производство {%s}. Использованы ресурсы: {%s} 
														
														//*****Pharmaceutical log codes**********
	public static final int LOG_PH_CHANGE_PARAMS = 38100;																			//Персонаж '%s' изменил закупочные цены 
	public static final int LOG_PH_PUT_ON_WAREHOUSE = 38200;																		//Персонаж '%s' выложил на склад {%s} 
	public static final int LOG_PH_PLAYER_SALE_RES = 38201;																			//Персонаж '%s' сдал {%s} за %s мнт. 
	public static final int LOG_PH_PLAYER_TAKE_FROM_WAREHOUSE = 38300;																//Персонаж '%s' забрал со склада {%s} 
	public static final int LOG_PH_CREATE_DRUGS = 38301;																			//Персонаж '%s' затратил {%s} на изготовление {%s} 

														//*****Clan center log codes**********
	public static final int LOG_NOF_PLAYER_BUY_CLAN = 52001;																		//Персонаж %s приобрел клан %s. Стоимость %s. 
	public static final int LOG_NOF_PLAYER_CREATE_POLL = 52002;																		//Персонаж %s создал голосование за кандидата %s. 
	public static final int LOG_NOF_CHANGE_LEADER = 52003;																			//Персонаж %s передал главенство клана персонажу %s.
	public static final int LOG_NOF_PLAYER_ADD_REQ = 52004;																			//Персонаж %s подал заявку на вступление в клан %s.
	public static final int LOG_NOF_PLAYER_DEC_CAP_AND_BALANCE = 52116;																//Получено %s за уменьшение размера офиса клана %s. Всего на счету: %s мнт. 
	public static final int LOG_NOF_PLAYER_DEC_CAP = 52117;																			//Персонаж %s уменьшил размер офиса до %s. 
	public static final int LOG_NOF_PLAYER_INC_CAP_AND_BALANCE = 52118;																//Заплачено %s за увеличение размера офиса клана %s. Всего на счету: %s мнт. 
	public static final int LOG_NOF_PLAYER_INC_CAP = 52119;																			//Персонаж %s увеличил размер офиса до %s 
	public static final int LOG_NOF_PLAYER_JOINED_CLAN_AND_BALANCE = 52122;															//В клан %s принят персонаж %s. Стоимость %s. Сумма на счету %s., 
	public static final int LOG_NOF_PLAYER_LEFT_CLAN_AND_BALANCE = 52123;															//Из клана %s исключен персонаж %s. Стоимость %s. Сумма на счету %s.
	public static final int LOG_NOF_PLAYER_PAYED_RENT_AND_BALANCE = 52120;															//Продлена аренда офиса клана %s. Стоимость %s. Сумма на счету %s. 
	public static final int LOG_NOF_PLAYER_PAYED_RENT = 52121;																		//Персонаж %s заплатил %s за продление аренды офиса. 
	 
	
	public static final String ULOG_FOR_LOL_FLOWERS = "1";																			//пусто - За доставку букета
	public static final String ULOG_FOR_LOL_NOP = "2";																				//пусто - бросил монетку
	
	public static final String ULOG_FOR_HEAL = "1";																					//за лечение,
	public static final String ULOG_FOR_PASSPORT = "2";																				//за получение паспорта
	public static final String ULOG_FOR_WIRE = "3";																					//за отправку телеграммы
	public static final String ULOG_FOR_PARCEL = "4";																				//за отправку посылки
	public static final String ULOG_FOR_TELEPORT = "5";																				//за телепортацию
	public static final String ULOG_FOR_CITIZEN_TAX = "6";																			//: налог на гражданство"
	public static final String ULOG_FOR_CLAN_TAX = "7";																				//: клановый налог"
	public static final String ULOG_FOR_RENT = "8";																					//: продление аренды здания"
	public static final String ULOG_FOR_BUILD_PURCHASE = "9";																		//: покупка здания"
	public static final String ULOG_FOR_BANK_CELL_PURCHASE = "10";																	//: покупка банковской ячейки"
	public static final String ULOG_FOR_BANK_PAPER_PURCHASE = "11";																	//: покупка банковского векселя" 
	public static final String ULOG_FOR_PROF_QUEST_PURCHASE = "12";																	//: покупка квеста для профессии"
	public static final String ULOG_FOR_STAT_FEE = "13";																			//за перераспределение параметров персонажа
	public static final String ULOG_FOR_ROAD_ORDER = "14";																			//: заказал строительство дороги на локации
	public static final String ULOG_FOR_SOUVENIR_DELIVERY = "16";																	// за доставку сувенира
	public static final String ULOG_FOR_WEDDING_RENT = "17";																		// :прокат одежды
	public static final String ULOG_FOR_GIFT_PACKAGING = "18";																		//за оформление подарка
	public static final String ULOG_FOR_RES_REFINE = "19";																			//за обогащение ресурсов
	public static final String ULOG_FOR_TRADEMARK ="20";																			//за регистрацию торговой марки
	public static final String ULOG_FOR_ALPHA_CODE = "25";																			//: оплата кода доступа альфа
	public static final String ULOG_FOR_ITEMS_PAINT = "26";																			//: покраска брони
	public static final String ULOG_FOR_BANK_CELL_RENT_RENEW = "28";																//: продление аренды банковской ячейки" 
	public static final String ULOG_FOR_CELL_KEY_DUBLICATE = "30";																	//: изготовление дубликата ключа от банковской ячейки
	public static final String ULOG_FOR_BULD_REQUEST = "33";																		//: заказ постройки здания
	public static final String ULOG_FOR_ITEM_MOD = "35";																			//: подгонка брони
	public static final String ULOG_FOR_BULD_UPGRAGE = "38";																		//за модернизацию здания
	public static final String ULOG_FOR_SURGERY = "39";																				//за хирургические изменения"
	public static final String ULOG_FOR_BARBER = "40";																				//за изменение причёски"

}
