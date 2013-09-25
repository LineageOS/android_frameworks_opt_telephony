/*
 * Copyright (C) 2013 The CyanogenMod Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.internal.telephony;

import java.util.Collections;
import java.util.Map;
import java.util.HashMap;

public class Operators{

    private static Map<String, String> operators = null;

    // Initialize list of Operator codes
    private static void initList() {
        // Setting capacity to 1489 and load factor to 1. 
        // Rehashes won't happen during initialization.
        // Change the initial capacity if more carriers are added.
        Map<String, String> init = new HashMap<String, String>(1489,1);
        init.put("41201","AWCC"); /*Afghanistan*/
        init.put("41240","Areeba"); /*Afghanistan*/
        init.put("41250","Etisalat"); /*Afghanistan*/
        init.put("41220","Roshan"); /*Afghanistan*/
        init.put("27601","AMC"); /*Albania*/
        init.put("27603","Eagle Mobile"); /*Albania*/
        init.put("27604","Plus Communication"); /*Albania*/
        init.put("27602","Vodafone"); /*Albania*/
        init.put("60302","Djezzy"); /*Algeria*/
        init.put("60301","Mobilis"); /*Algeria*/
        init.put("60303","Nedjma"); /*Algeria*/
        init.put("54411","Bluesky"); /*American Samoa*/
        init.put("21303","Mobiland"); /*Andorra*/
        init.put("63104","MOVICEL"); /*Angola*/
        init.put("63102","UNITEL"); /*Angola*/
        init.put("365840","Cable & Wireless"); /*Anguilla (United Kingdom)*/
        init.put("365010","Weblinks Limited"); /*Anguilla (United Kingdom)*/
        init.put("344030","APUA"); /*Antigua and Barbuda*/
        init.put("344920","bmobile"); /*Antigua and Barbuda*/
        init.put("344930","Digicel"); /*Antigua and Barbuda*/
        init.put("722310","Claro"); /*Argentina*/
        init.put("722320","Claro"); /*Argentina*/
        init.put("722330","Claro"); /*Argentina*/
        init.put("722350","Hutchinson (PORT HABLE)"); /*Argentina*/
        init.put("722010","Movistar"); /*Argentina*/
        init.put("722070","Movistar"); /*Argentina*/
        init.put("722020","Nextel"); /*Argentina*/
        init.put("72234","Personal"); /*Argentina*/
        init.put("722341","Telecom Personal SA"); /*Argentina*/
        init.put("72236","Telecom Personal SA"); /*Argentina*/
        init.put("28301","Beeline"); /*Armenia*/
        init.put("28310","Orange"); /*Armenia*/
        init.put("28305","VivaCell-MTS"); /*Armenia*/
        init.put("36302","Digicel"); /*Aruba (Netherlands)*/
        init.put("36320","Digicell"); /*Aruba (Netherlands)*/
        init.put("36301","SETAR"); /*Aruba (Netherlands)*/
        init.put("50506","3"); /*Australia*/
        init.put("50512","3"); /*Australia*/
        init.put("50515","3GIS"); /*Australia*/
        init.put("50514","AAPT"); /*Australia*/
        init.put("50524","Advanced Communications Technologies"); /*Australia*/
        init.put("50509","Airnet"); /*Australia*/
        init.put("50538","Crazy John's"); /*Australia*/
        init.put("50504","Department of Defence"); /*Australia*/
        init.put("50588","Localstar"); /*Australia*/
        init.put("50510","Norfolk Telecom"); /*Australia*/
        init.put("50508","One. Tel"); /*Australia*/
        init.put("50599","One. Tel"); /*Australia*/
        init.put("50502","OPTUS / Virgin Mobile"); /*Australia*/
        init.put("50505","Ozitel"); /*Australia*/
        init.put("50513","Railcorp"); /*Australia*/
        init.put("50521","SOUL"); /*Australia*/
        init.put("50501","Telstra"); /*Australia*/
        init.put("50511","Telstra"); /*Australia*/
        init.put("50571","Telstra"); /*Australia*/
        init.put("50572","Telstra"); /*Australia*/
        init.put("50516","Victorian Rail Track"); /*Australia*/
        init.put("50503","Vodafone"); /*Australia*/
        init.put("50507","Vodafone"); /*Australia*/
        init.put("50590","YES OPTUS"); /*Australia*/
        init.put("23210","3"); /*Austria*/
        init.put("23214","3"); /*Austria*/
        init.put("23201","A1"); /*Austria*/
        init.put("23209","A1"); /*Austria*/
        init.put("23215","Barablu"); /*Austria*/
        init.put("23211","bob"); /*Austria*/
        init.put("23291","GSM-R A"); /*Austria*/
        init.put("23205","Orange"); /*Austria*/
        init.put("23203","T-Mobile"); /*Austria*/
        init.put("23207","T-Mobile"); /*Austria*/
        init.put("23212","yesss"); /*Austria*/
        init.put("40001","Azercell"); /*Azerbaijan*/
        init.put("40002","Bakcell"); /*Azerbaijan*/
        init.put("40003","FONEX"); /*Azerbaijan*/
        init.put("40004","Nar Mobile"); /*Azerbaijan*/
        init.put("364390","BaTelCo"); /*Bahamas*/
        init.put("42601","Batelco"); /*Bahrain*/
        init.put("42602","MTC-VFBH"); /*Bahrain*/
        init.put("42604","VIVA"); /*Bahrain*/
        init.put("47002","Aktel"); /*Bangladesh*/
        init.put("47003","Banglalink"); /*Bangladesh*/
        init.put("47005","Citycell"); /*Bangladesh*/
        init.put("47006","Citycell"); /*Bangladesh*/
        init.put("47001","Grameenphone"); /*Bangladesh*/
        init.put("47004","TeleTalk"); /*Bangladesh*/
        init.put("47007","Warid"); /*Bangladesh*/
        init.put("342600","bmobile"); /*Barbados*/
        init.put("342750","Digicel"); /*Barbados*/
        init.put("342820","Sunbeach Communications"); /*Barbados*/
        init.put("257501","BelCel JV"); /*Belarus*/
        init.put("25703","DIALLOG"); /*Belarus*/
        init.put("25704","life:)"); /*Belarus*/
        init.put("25702","MTS"); /*Belarus*/
        init.put("25701","Velcom"); /*Belarus*/
        init.put("20620","BASE"); /*Belgium*/
        init.put("20610","Mobistar"); /*Belgium*/
        init.put("20601","Proximus"); /*Belgium*/
        init.put("20605","Telenet"); /*Belgium*/
        init.put("70267","Belize Telemedia"); /*Belize*/
        init.put("70268","International Telecommunications Ltd."); /*Belize*/
        init.put("70299","Smart"); /*Belize*/
        init.put("61603","Areeba"); /*Benin*/
        init.put("61600","BBCOM"); /*Benin*/
        init.put("61604","BBCOM"); /*Benin*/
        init.put("61605","Glo"); /*Benin*/
        init.put("61601","Libercom"); /*Benin*/
        init.put("61602","Telecel"); /*Benin*/
        init.put("31038","Digicel"); /*Bermudas*/
        init.put("35001","Digicel Bermuda"); /*Bermudas*/
        init.put("35002","Mobility"); /*Bermudas*/
        init.put("40211","B-Mobile"); /*Bhutan*/
        init.put("40277","TashiCell"); /*Bhutan*/
        init.put("73602","Entel"); /*Bolivia*/
        init.put("73601","Nuevatel"); /*Bolivia*/
        init.put("73603","Tigo"); /*Bolivia*/
        init.put("21890","BH Mobile"); /*Bosnia and Herzegovina*/
        init.put("21803","ERONET"); /*Bosnia and Herzegovina*/
        init.put("21805","m:tel"); /*Bosnia and Herzegovina*/
        init.put("65204","BTC Mobile"); /*Botswana*/
        init.put("65201","Mascom"); /*Botswana*/
        init.put("65202","Orange"); /*Botswana*/
        init.put("72437","aiou"); /*Brazil*/
        init.put("72424","Amazonia Celular"); /*Brazil*/
        init.put("72416","Brasil Telecom"); /*Brazil*/
        init.put("72405","Claro"); /*Brazil*/
        init.put("72432","CTBC Cellular"); /*Brazil*/
        init.put("72433","CTBC Cellular"); /*Brazil*/
        init.put("72434","CTBC Cellular"); /*Brazil*/
        init.put("72407","CTBC Celular"); /*Brazil*/
        init.put("72400","Nextel"); /*Brazil*/
        init.put("72439","Nextel"); /*Brazil*/
        init.put("72415","Sercomtel"); /*Brazil*/
        init.put("72402","TIM"); /*Brazil*/
        init.put("72403","TIM"); /*Brazil*/
        init.put("72404","TIM"); /*Brazil*/
        init.put("72408","TIM"); /*Brazil*/
        init.put("72431","TNL PCS"); /*Brazil*/
        init.put("72406","Vivo"); /*Brazil*/
        init.put("72410","Vivo"); /*Brazil*/
        init.put("72411","Vivo"); /*Brazil*/
        init.put("72423","Vivo"); /*Brazil*/
        init.put("348170","Cable & Wireless"); /*British Virgin Islands (United Kingdom)*/
        init.put("348570","Caribbean Cellular Telephone"); /*British Virgin Islands (United Kingdom)*/
        init.put("348770","Digicel"); /*British Virgin Islands (United Kingdom)*/
        init.put("52802","B-Mobile"); /*Brunei*/
        init.put("52811","DTSCom"); /*Brunei*/
        init.put("52801","Jabatan Telekom"); /*Brunei*/
        init.put("28405","GLOBUL"); /*Bulgaria*/
        init.put("28401","M-Tel"); /*Bulgaria*/
        init.put("28404","Undisclosed"); /*Bulgaria*/
        init.put("28403","Vivatel"); /*Bulgaria*/
        init.put("61301","Onatel"); /*Burkina Faso*/
        init.put("61303","Telecel Faso"); /*Burkina Faso*/
        init.put("61302","Zain"); /*Burkina Faso*/
        init.put("64202","Africell"); /*Burundi*/
        init.put("64208","HiTs Telecom"); /*Burundi*/
        init.put("64207","Smart Mobile"); /*Burundi*/
        init.put("64201","Spacetel"); /*Burundi*/
        init.put("64203","Telecel"); /*Burundi*/
        init.put("64282","U-COM Burundi"); /*Burundi*/
        init.put("45609","Beeline"); /*Cambodia*/
        init.put("45618","Camshin / Shinawatra"); /*Cambodia*/
        init.put("45611","Excell"); /*Cambodia*/
        init.put("45602","hello"); /*Cambodia*/
        init.put("45608","Metfone"); /*Cambodia*/
        init.put("45601","Mobitel"); /*Cambodia*/
        init.put("45604","qb"); /*Cambodia*/
        init.put("45603","S Telecom"); /*Cambodia*/
        init.put("45606","Smart Mobile"); /*Cambodia*/
        init.put("45605","Star-Cell"); /*Cambodia*/
        init.put("62401","MTN Cameroon"); /*Cameroon*/
        init.put("62402","Orange"); /*Cameroon*/
        init.put("302290","Airtel Wireless"); /*Canada*/
        init.put("302652","BC Tel Mobility"); /*Canada*/
        init.put("302610","Bell"); /*Canada*/
        init.put("302640","Bell"); /*Canada*/
        init.put("302880","Bell / Telus / SaskTel"); /*Canada*/
        init.put("302651","Bell Mobility"); /*Canada*/
        init.put("302380","DMTS"); /*Canada*/
        init.put("302370","Fido"); /*Canada*/
        init.put("302350","FIRST"); /*Canada*/
        init.put("302710","Globalstar"); /*Canada*/
        init.put("302620","ICE Wireless"); /*Canada*/
        init.put("302701","MB Tel Mobility"); /*Canada*/
        init.put("302320","Mobilicity"); /*Canada*/
        init.put("302702","MT&T Mobility"); /*Canada*/
        init.put("302660","MTS"); /*Canada*/
        init.put("302655","MTS Mobility"); /*Canada*/
        init.put("302703","New Tel Mobility"); /*Canada*/
        init.put("302720","Rogers Wireless"); /*Canada*/
        init.put("302654","Sask Tel Mobility"); /*Canada*/
        init.put("302680","SaskTel"); /*Canada*/
        init.put("302780","SaskTel"); /*Canada*/
        init.put("302656","Tbay Mobility"); /*Canada*/
        init.put("302220","Telus"); /*Canada*/
        init.put("302221","Telus"); /*Canada*/
        init.put("302657","Telus (Quebec) Mobility"); /*Canada*/
        init.put("302360","Telus Mobility"); /*Canada*/
        init.put("302361","Telus Mobility"); /*Canada*/
        init.put("302653","Telus Mobility"); /*Canada*/
        init.put("302500","Videotron"); /*Canada*/
        init.put("302510","Videotron"); /*Canada*/
        init.put("302490","WIND Mobile"); /*Canada*/
        init.put("62501","CVMOVEL"); /*Cape Verde*/
        init.put("62502","T+"); /*Cape Verde*/
        init.put("346140","Cable & Wireless"); /*Cayman Islands (United Kingdom)*/
        init.put("346050","Digicel"); /*Cayman Islands (United Kingdom)*/
        init.put("62301","CTP"); /*Central African Republic*/
        init.put("62304","Nationlink"); /*Central African Republic*/
        init.put("62303","Orange"); /*Central African Republic*/
        init.put("62302","TC"); /*Central African Republic*/
        init.put("73003","Claro"); /*Chile*/
        init.put("73001","Entel"); /*Chile*/
        init.put("73010","Entel"); /*Chile*/
        init.put("73002","movistar"); /*Chile*/
        init.put("73004","Nextel"); /*Chile*/
        init.put("73008","VTR MOvil"); /*Chile*/
        init.put("73099","Will"); /*Chile*/
        init.put("46006","(unknown)"); /*China*/
        init.put("46000","China Mobile"); /*China*/
        init.put("46002","China Mobile"); /*China*/
        init.put("46007","China Mobile"); /*China*/
        init.put("46003","China Telecom"); /*China*/
        init.put("46005","China Telecom"); /*China*/
        init.put("46020","China Tietong"); /*China*/
        init.put("46001","China Unicom"); /*China*/
        init.put("732001","Colombia Telecomunicaciones S.A. - Telecom"); /*Colombia*/
        init.put("732101","Comcel"); /*Colombia*/
        init.put("732002","Edatel"); /*Colombia*/
        init.put("732102","movistar"); /*Colombia*/
        init.put("732123","movistar"); /*Colombia*/
        init.put("732103","Tigo"); /*Colombia*/
        init.put("732111","Tigo"); /*Colombia*/
        init.put("65401","HURI - SNPT"); /*Comoros*/
        init.put("54801","Telecom Cook"); /*Cook Islands*/
        init.put("71201","ICE"); /*Costa Rica*/
        init.put("71202","ICE"); /*Costa Rica*/
        init.put("71203","ICE"); /*Costa Rica*/
        init.put("71204","movistar"); /*Costa Rica*/
        init.put("61201","Cora de Comstar"); /*Cote d'Ivoire*/
        init.put("61204","KoZ"); /*Cote d'Ivoire*/
        init.put("61202","Moov"); /*Cote d'Ivoire*/
        init.put("61205","MTN"); /*Cote d'Ivoire*/
        init.put("61203","Orange"); /*Cote d'Ivoire*/
        init.put("61206","ORICEL"); /*Cote d'Ivoire*/
        init.put("21901","T-Mobile"); /*Croatia*/
        init.put("21902","Tele2"); /*Croatia*/
        init.put("21910","VIPnet"); /*Croatia*/
        init.put("36801","ETECSA"); /*Cuba*/
        init.put("28001","Cytamobile-Vodafone"); /*Cyprus*/
        init.put("28010","MTN"); /*Cyprus*/
        init.put("62204","Salam"); /*Czad*/
        init.put("62202","TAWALI"); /*Czad*/
        init.put("62203","TIGO - Millicom"); /*Czad*/
        init.put("62201","Zain"); /*Czad*/
        init.put("23002","EUROTEL PRAHA"); /*Czech Republic*/
        init.put("23003","OSKAR"); /*Czech Republic*/
        init.put("23006","OSNO TELECOMUNICATION, s.r.o."); /*Czech Republic*/
        init.put("23098","SeDC s.o."); /*Czech Republic*/
        init.put("23001","T-Mobile"); /*Czech Republic*/
        init.put("23005","TRAVEL TELEKOMMUNIKATION, s.r.o."); /*Czech Republic*/
        init.put("23004","U:fon"); /*Czech Republic*/
        init.put("23099","Vodafone Czech Republic a.s., R&D Centre at FEE, CTU"); /*Czech Republic*/
        init.put("63086","CCT"); /*Democratic Republic of Congo*/
        init.put("63004","Cellco"); /*Democratic Republic of Congo*/
        init.put("63010","Libertis Telecom"); /*Democratic Republic of Congo*/
        init.put("63089","SAIT Telecom"); /*Democratic Republic of Congo*/
        init.put("63005","Supercell"); /*Democratic Republic of Congo*/
        init.put("63001","Vodacom"); /*Democratic Republic of Congo*/
        init.put("63002","Zain"); /*Democratic Republic of Congo*/
        init.put("23806","3"); /*Denmark*/
        init.put("23805","ApS KBUS"); /*Denmark*/
        init.put("23807","Barablu Mobile Ltd."); /*Denmark*/
        init.put("23809","Dansk Beredskabskommunikation A/S"); /*Denmark*/
        init.put("23811","Dansk Beredskabskommunikation A/S"); /*Denmark*/
        init.put("23840","Ericsson Danmark A/S"); /*Denmark*/
        init.put("23812","Lycamobile Denmark Ltd"); /*Denmark*/
        init.put("23803","MIGway A/S"); /*Denmark*/
        init.put("23877","Sonofon"); /*Denmark*/
        init.put("23801","TDC"); /*Denmark*/
        init.put("23810","TDC"); /*Denmark*/
        init.put("23802","Telenor"); /*Denmark*/
        init.put("23820","Telia"); /*Denmark*/
        init.put("23830","Telia"); /*Denmark*/
        init.put("63801","Evatis"); /*Djibouti*/
        init.put("366110","Cable & Wireless"); /*Dominica*/
        init.put("366020","Digicel"); /*Dominica*/
        init.put("37002","Claro"); /*Dominican Republic*/
        init.put("37001","Orange"); /*Dominican Republic*/
        init.put("37003","Tricom S.A."); /*Dominican Republic*/
        init.put("37004","ViVa"); /*Dominican Republic*/
        init.put("51402","Timor Telecom"); /*East Timor*/
        init.put("74002","Alegro"); /*Ecuador*/
        init.put("74000","Movistar"); /*Ecuador*/
        init.put("74001","Porta"); /*Ecuador*/
        init.put("60203","Etisalat"); /*Egypt*/
        init.put("60201","Mobinil"); /*Egypt*/
        init.put("60202","Vodafone"); /*Egypt*/
        init.put("70610","Claro"); /*El Salvador*/
        init.put("70611","Claro"); /*El Salvador*/
        init.put("70601","CTE Telecom Personal"); /*El Salvador*/
        init.put("70602","digicel"); /*El Salvador*/
        init.put("70604","movistar"); /*El Salvador*/
        init.put("70603","Telemovil EL Salvador"); /*El Salvador*/
        init.put("62703","Hits GQ"); /*Equatorial Guinea*/
        init.put("62701","Orange GQ"); /*Equatorial Guinea*/
        init.put("65701","Eritel"); /*Eritrea*/
        init.put("24805","AS Bravocom Mobiil"); /*Estonia*/
        init.put("24802","Elisa"); /*Estonia*/
        init.put("24801","EMT"); /*Estonia*/
        init.put("24804","OY Top Connect"); /*Estonia*/
        init.put("24806","OY ViaTel"); /*Estonia*/
        init.put("24803","Tele 2"); /*Estonia*/
        init.put("63601","ETMTN"); /*Ethiopia*/
        init.put("28801","Faroese Telecom"); /*Faroe Islands (Denmark)*/
        init.put("28802","Vodafone"); /*Faroe Islands (Denmark)*/
        init.put("54202","Digicel"); /*Fiji*/
        init.put("54201","Vodafone"); /*Fiji*/
        init.put("24414","AMT"); /*Finland*/
        init.put("24403","DNA"); /*Finland*/
        init.put("24412","DNA"); /*Finland*/
        init.put("24405","Elisa"); /*Finland*/
        init.put("24407","Nokia"); /*Finland*/
        init.put("24415","SAMK"); /*Finland*/
        init.put("24421","Saunalahti"); /*Finland*/
        init.put("24429","Scnl Truphone"); /*Finland*/
        init.put("24491","Sonera"); /*Finland*/
        init.put("24410","TDC Oy"); /*Finland*/
        init.put("24408","Unknown"); /*Finland*/
        init.put("24411","VIRVE"); /*Finland*/
        init.put("20820","Bouygues"); /*France*/
        init.put("20821","Bouygues"); /*France*/
        init.put("20888","Bouygues"); /*France*/
        init.put("20801","France Telecom Mobile"); /*France*/
        init.put("20814","Free Mobile"); /*France*/
        init.put("20815","Free Mobile"); /*France*/
        init.put("20805","Globalstar Europe"); /*France*/
        init.put("20806","Globalstar Europe"); /*France*/
        init.put("20807","Globalstar Europe"); /*France*/
        init.put("20800","Orange"); /*France*/
        init.put("20802","Orange"); /*France*/
        init.put("20810","SFR"); /*France*/
        init.put("20811","SFR"); /*France*/
        init.put("20813","SFR"); /*France*/
        init.put("20822","Transatel Mobile"); /*France*/
        init.put("54720","VINI"); /*French Polynesia (France)*/
        init.put("62804","Azur"); /*Gabon*/
        init.put("62801","Libertis"); /*Gabon*/
        init.put("62802","Moov (Telecel) Gabon S.A."); /*Gabon*/
        init.put("62803","Zain"); /*Gabon*/
        init.put("60702","Africel"); /*Gambia*/
        init.put("60703","Comium"); /*Gambia*/
        init.put("60701","Gamcel"); /*Gambia*/
        init.put("60704","QCell"); /*Gambia*/
        init.put("28988","A-Mobile"); /*Georgia*/
        init.put("28967","Aquafon"); /*Georgia*/
        init.put("28204","Beeline"); /*Georgia*/
        init.put("28201","Geocell"); /*Georgia*/
        init.put("28203","Iberiatel"); /*Georgia*/
        init.put("28202","Magti"); /*Georgia*/
        init.put("28205","Silknet"); /*Georgia*/
        init.put("26242","27C3"); /*Germany*/
        init.put("26215","Airdata"); /*Germany*/
        init.put("26210","Arcor AG & Co"); /*Germany*/
        init.put("26260","DB Telematik"); /*Germany*/
        init.put("262901","Debitel"); /*Germany*/
        init.put("26212","Dolphin Telecom"); /*Germany*/
        init.put("26203","E-Plus"); /*Germany*/
        init.put("26205","E-Plus"); /*Germany*/
        init.put("26277","E-Plus"); /*Germany*/
        init.put("26214","Group 3G UMTS"); /*Germany*/
        init.put("26243","LYCA"); /*Germany*/
        init.put("26213","Mobilcom Multimedia"); /*Germany*/
        init.put("26292","Nash Technologies"); /*Germany*/
        init.put("26207","O2"); /*Germany*/
        init.put("26208","O2"); /*Germany*/
        init.put("26211","O2"); /*Germany*/
        init.put("26276","Siemens AG"); /*Germany*/
        init.put("26201","T-Mobile"); /*Germany*/
        init.put("26206","T-Mobile"); /*Germany*/
        init.put("26216","vistream"); /*Germany*/
        init.put("26202","Vodafone"); /*Germany*/
        init.put("26204","Vodafone"); /*Germany*/
        init.put("26209","Vodafone"); /*Germany*/
        init.put("62006","Airtel"); /*Ghana*/
        init.put("62002","Ghana Telecom Mobile"); /*Ghana*/
        init.put("62004","Kasapa / Hutchison Telecom"); /*Ghana*/
        init.put("62001","MTN"); /*Ghana*/
        init.put("62003","tiGO"); /*Ghana*/
        init.put("26606","CTS Mobile"); /*Gibraltar (United Kingdom)*/
        init.put("26601","GibTel"); /*Gibraltar (United Kingdom)*/
        init.put("20201","Cosmote"); /*Greece*/
        init.put("20205","Vodafone"); /*Greece*/
        init.put("20209","Wind"); /*Greece*/
        init.put("20210","Wind"); /*Greece*/
        init.put("29001","TELE Greenland A/S"); /*Greenland (Denmark)*/
        init.put("352110","Cable & Wireless"); /*Grenada*/
        init.put("352030","Digicel"); /*Grenada*/
        init.put("34020","Digicel"); /*Guadeloupe (France)*/
        init.put("34008","MIO GSM"); /*Guadeloupe (France)*/
        init.put("34001","Orange"); /*Guadeloupe (France)*/
        init.put("34002","Outremer"); /*Guadeloupe (France)*/
        init.put("34003","Telcell"); /*Guadeloupe (France)*/
        init.put("310033","Guam Telephone Authority"); /*Guam (United States)*/
        init.put("310370","Guamcell"); /*Guam (United States)*/
        init.put("310470","Guamcell"); /*Guam (United States)*/
        init.put("311250","i CAN_GSM"); /*Guam (United States)*/
        init.put("310032","IT&E Wireless"); /*Guam (United States)*/
        init.put("310140","mPulse"); /*Guam (United States)*/
        init.put("70401","Claro"); /*Guatemala*/
        init.put("70402","Comcel / Tigo"); /*Guatemala*/
        init.put("70403","movistar"); /*Guatemala*/
        init.put("73801","Digicel"); /*Guiana*/
        init.put("73802","GT&T Cellink Plus"); /*Guiana*/
        init.put("61105","Cellcom"); /*Guinea*/
        init.put("61102","Lagui"); /*Guinea*/
        init.put("61104","MTN"); /*Guinea*/
        init.put("61101","Spacetel"); /*Guinea*/
        init.put("61103","Telecel Guinee"); /*Guinea*/
        init.put("63202","Areeba"); /*Guinea-Bissau*/
        init.put("63203","Orange"); /*Guinea-Bissau*/
        init.put("372010","Comcel / Voila"); /*Haiti*/
        init.put("37202","Digicel"); /*Haiti*/
        init.put("37203","NATCOM"); /*Haiti*/
        init.put("20414","6Gmobile"); /*Holland (Netherlands)*/
        init.put("20423","ASPIDER Solutions Nederland B.V."); /*Holland (Netherlands)*/
        init.put("20427","Breezz Nederland B.V."); /*Holland (Netherlands)*/
        init.put("20425","CapX B.V."); /*Holland (Netherlands)*/
        init.put("20407","eleena (MVNE)"); /*Holland (Netherlands)*/
        init.put("20405","Elephant Talk Communications Premium Rate Services"); /*Holland (Netherlands)*/
        init.put("20417","Intercity Mobile Communications B.V."); /*Holland (Netherlands)*/
        init.put("20408","KPN"); /*Holland (Netherlands)*/
        init.put("20410","KPN"); /*Holland (Netherlands)*/
        init.put("20469","KPN Mobile The Netherlands B.V."); /*Holland (Netherlands)*/
        init.put("20409","Lycamobile"); /*Holland (Netherlands)*/
        init.put("20422","Ministerie van Defensie"); /*Holland (Netherlands)*/
        init.put("20419","Mixe Communication Solutions B.V."); /*Holland (Netherlands)*/
        init.put("20406","Mundio Mobile (Netherlands) Ltd"); /*Holland (Netherlands)*/
        init.put("20421","NS Railinfrabeheer B.V."); /*Holland (Netherlands)*/
        init.put("20420","Orange Nederland"); /*Holland (Netherlands)*/
        init.put("20424","Private Mobility Nederland B.V."); /*Holland (Netherlands)*/
        init.put("20467","RadioAccess B.V."); /*Holland (Netherlands)*/
        init.put("20426","SpeakUp B.V."); /*Holland (Netherlands)*/
        init.put("20416","T-Mobile / Ben"); /*Holland (Netherlands)*/
        init.put("20402","Tele2 Netherlands"); /*Holland (Netherlands)*/
        init.put("20412","Telfort / O2"); /*Holland (Netherlands)*/
        init.put("20413","Unica Installatietechniek B.V"); /*Holland (Netherlands)*/
        init.put("20468","Unify Group Holding B.V."); /*Holland (Netherlands)*/
        init.put("20418","UPC Nederland B.V."); /*Holland (Netherlands)*/
        init.put("20401","VastMobiel B.V."); /*Holland (Netherlands)*/
        init.put("20404","Vodafone"); /*Holland (Netherlands)*/
        init.put("20403","Voiceworks B.V."); /*Holland (Netherlands)*/
        init.put("70802","Celtel / Tigo"); /*Honduras*/
        init.put("70801","Claro"); /*Honduras*/
        init.put("70840","DIGICEL"); /*Honduras*/
        init.put("70830","Hondutel"); /*Honduras*/
        init.put("45403","3 (3G)"); /*Hong Kong (People's Republic of China)*/
        init.put("45405","3 CDMA"); /*Hong Kong (People's Republic of China)*/
        init.put("45404","3 Dual (2G)"); /*Hong Kong (People's Republic of China)*/
        init.put("45412","C Peoples"); /*Hong Kong (People's Republic of China)*/
        init.put("45409","China Motion Telecom"); /*Hong Kong (People's Republic of China)*/
        init.put("45407","China Unicom"); /*Hong Kong (People's Republic of China)*/
        init.put("45411","China-Hongkong Telecom"); /*Hong Kong (People's Republic of China)*/
        init.put("45401","CITIC Telecom 1616"); /*Hong Kong (People's Republic of China)*/
        init.put("45400","CSL"); /*Hong Kong (People's Republic of China)*/
        init.put("45402","CSL 3G"); /*Hong Kong (People's Republic of China)*/
        init.put("45418","Hong Kong CSL Limited"); /*Hong Kong (People's Republic of China)*/
        init.put("45414","Hutchison Telecom"); /*Hong Kong (People's Republic of China)*/
        init.put("45410","New World"); /*Hong Kong (People's Republic of China)*/
        init.put("45416","PCCW"); /*Hong Kong (People's Republic of China)*/
        init.put("45419","PCCW"); /*Hong Kong (People's Republic of China)*/
        init.put("45429","PCCW"); /*Hong Kong (People's Republic of China)*/
        init.put("45415","SmarTone Mobile Comms"); /*Hong Kong (People's Republic of China)*/
        init.put("45417","SmarTone Mobile Comms"); /*Hong Kong (People's Republic of China)*/
        init.put("45406","Smartone-Vodafone"); /*Hong Kong (People's Republic of China)*/
        init.put("45408","Trident"); /*Hong Kong (People's Republic of China)*/
        init.put("21601","Pannon"); /*Hungary*/
        init.put("21630","T-Mobile"); /*Hungary*/
        init.put("21670","Vodafone"); /*Hungary*/
        init.put("27407","IceCell"); /*Iceland*/
        init.put("27411","Nova"); /*Iceland*/
        init.put("27406","N'll nIu ehf"); /*Iceland*/
        init.put("27408","On-waves"); /*Iceland*/
        init.put("27401","Siminn"); /*Iceland*/
        init.put("27412","Tal"); /*Iceland*/
        init.put("27404","Viking"); /*Iceland*/
        init.put("27402","Vodafone"); /*Iceland*/
        init.put("27403","Vodafone"); /*Iceland*/
        init.put("40417","AIRCEL"); /*India*/
        init.put("40425","AIRCEL"); /*India*/
        init.put("40428","AIRCEL"); /*India*/
        init.put("40429","AIRCEL"); /*India*/
        init.put("40437","AIRCEL"); /*India*/
        init.put("40491","AIRCEL"); /*India*/
        init.put("405082","AIRCEL"); /*India*/
        init.put("405800","AIRCEL"); /*India*/
        init.put("405801","AIRCEL"); /*India*/
        init.put("405802","AIRCEL"); /*India*/
        init.put("405803","AIRCEL"); /*India*/
        init.put("405804","AIRCEL"); /*India*/
        init.put("405805","AIRCEL"); /*India*/
        init.put("405806","AIRCEL"); /*India*/
        init.put("405807","AIRCEL"); /*India*/
        init.put("405808","AIRCEL"); /*India*/
        init.put("405809","AIRCEL"); /*India*/
        init.put("405810","AIRCEL"); /*India*/
        init.put("405811","AIRCEL"); /*India*/
        init.put("405812","AIRCEL"); /*India*/
        init.put("405813","AIRCEL"); /*India*/
        init.put("40460","Aircell Digilink"); /*India*/
        init.put("40415","Aircell Digilink Essar Cellph."); /*India*/
        init.put("40406","Airtel"); /*India*/
        init.put("40410","Airtel"); /*India*/
        init.put("40431","Airtel"); /*India*/
        init.put("40440","Airtel"); /*India*/
        init.put("40445","Airtel"); /*India*/
        init.put("40449","Airtel"); /*India*/
        init.put("40470","Airtel"); /*India*/
        init.put("40494","Airtel"); /*India*/
        init.put("40495","Airtel"); /*India*/
        init.put("40497","Airtel"); /*India*/
        init.put("40498","Airtel"); /*India*/
        init.put("40551","Airtel"); /*India*/
        init.put("40552","Airtel"); /*India*/
        init.put("40553","AirTel"); /*India*/
        init.put("40554","AirTel"); /*India*/
        init.put("40555","AirTel"); /*India*/
        init.put("40556","AirTel"); /*India*/
        init.put("40570","AirTel"); /*India*/
        init.put("40496","Airtel - Haryana"); /*India*/
        init.put("40402","Airtel - Punjab"); /*India*/
        init.put("40403","Airtel / Bharti Telenet"); /*India*/
        init.put("40493","Airtel Gujrat"); /*India*/
        init.put("40490","Airtel Maharashtra & Goa"); /*India*/
        init.put("40492","Airtel Mumbai"); /*India*/
        init.put("40443","BPL Mobile Cellular"); /*India*/
        init.put("40421","BPL Mobile Mumbai"); /*India*/
        init.put("40427","BPL USWest Cellular / Cellular Comms"); /*India*/
        init.put("40434","BSNL"); /*India*/
        init.put("40438","BSNL"); /*India*/
        init.put("40451","BSNL"); /*India*/
        init.put("40453","BSNL"); /*India*/
        init.put("40454","BSNL"); /*India*/
        init.put("40455","BSNL"); /*India*/
        init.put("40457","BSNL"); /*India*/
        init.put("40458","BSNL"); /*India*/
        init.put("40459","BSNL"); /*India*/
        init.put("40464","BSNL"); /*India*/
        init.put("40471","BSNL"); /*India*/
        init.put("40473","BSNL"); /*India*/
        init.put("40474","BSNL"); /*India*/
        init.put("40475","BSNL"); /*India*/
        init.put("40476","BSNL"); /*India*/
        init.put("40477","BSNL"); /*India*/
        init.put("40480","BSNL"); /*India*/
        init.put("40481","BSNL"); /*India*/
        init.put("40462","BSNL J&K"); /*India*/
        init.put("40472","BSNL Kerala"); /*India*/
        init.put("40466","BSNL Maharashtra & Goa"); /*India*/
        init.put("40478","BTA Cellcom"); /*India*/
        init.put("40448","Dishnet Wireless"); /*India*/
        init.put("40482","Escorts"); /*India*/
        init.put("40487","Escorts Telecom"); /*India*/
        init.put("40488","Escorts Telecom"); /*India*/
        init.put("40489","Escorts Telecom"); /*India*/
        init.put("40411","Essar / Sterling Cellular"); /*India*/
        init.put("405912","Etisalat DB(cheers)"); /*India*/
        init.put("405913","Etisalat DB(cheers)"); /*India*/
        init.put("405914","Etisalat DB(cheers)"); /*India*/
        init.put("405917","Etisalat DB(cheers)"); /*India*/
        init.put("40566","Hutch"); /*India*/
        init.put("40486","Hutchinson Essar South"); /*India*/
        init.put("40413","Hutchison Essar South"); /*India*/
        init.put("40484","Hutchison Essar South"); /*India*/
        init.put("40419","IDEA"); /*India*/
        init.put("405799","IDEA"); /*India*/
        init.put("405845","IDEA"); /*India*/
        init.put("405848","IDEA"); /*India*/
        init.put("405850","IDEA"); /*India*/
        init.put("40586","IDEA"); /*India*/
        init.put("40412","Idea (Escotel) Haryana"); /*India*/
        init.put("40456","Idea (Escotel) UP West"); /*India*/
        init.put("40404","IDEA CELLULAR - Delhi"); /*India*/
        init.put("40424","IDEA Cellular - Gujarat"); /*India*/
        init.put("40422","IDEA Cellular - Maharashtra"); /*India*/
        init.put("405855","Loop Mobile"); /*India*/
        init.put("405864","Loop Mobile"); /*India*/
        init.put("405865","Loop Mobile"); /*India*/
        init.put("40468","MTNL - Delhi"); /*India*/
        init.put("40469","MTNL - Mumbai"); /*India*/
        init.put("40450","Reliance"); /*India*/
        init.put("40452","Reliance"); /*India*/
        init.put("40467","Reliance"); /*India*/
        init.put("40483","Reliance"); /*India*/
        init.put("40485","Reliance"); /*India*/
        init.put("40501","Reliance"); /*India*/
        init.put("40503","Reliance"); /*India*/
        init.put("40504","Reliance"); /*India*/
        init.put("40509","Reliance"); /*India*/
        init.put("40510","Reliance"); /*India*/
        init.put("40513","Reliance"); /*India*/
        init.put("40409","Reliance Telecom Private"); /*India*/
        init.put("40436","Reliance Telecom Private"); /*India*/
        init.put("40441","RPG MAA"); /*India*/
        init.put("405881","S Tel"); /*India*/
        init.put("40444","Spice Telecom - Karnataka"); /*India*/
        init.put("40414","Spice Telecom - Punjab"); /*India*/
        init.put("40442","Srinivas Cellcom / Aircel"); /*India*/
        init.put("40407","TATA Cellular / Idea Cellular"); /*India*/
        init.put("405025","TATA Teleservice"); /*India*/
        init.put("405026","TATA Teleservice"); /*India*/
        init.put("405027","TATA Teleservice"); /*India*/
        init.put("405029","TATA Teleservice"); /*India*/
        init.put("405030","TATA Teleservice"); /*India*/
        init.put("405031","TATA Teleservice"); /*India*/
        init.put("405032","TATA Teleservice"); /*India*/
        init.put("405033","TATA Teleservice"); /*India*/
        init.put("405034","TATA Teleservice"); /*India*/
        init.put("405035","TATA Teleservice"); /*India*/
        init.put("405036","TATA Teleservice"); /*India*/
        init.put("405037","TATA Teleservice"); /*India*/
        init.put("405038","TATA Teleservice"); /*India*/
        init.put("405039","TATA Teleservice"); /*India*/
        init.put("405040","TATA Teleservice"); /*India*/
        init.put("405041","TATA Teleservice"); /*India*/
        init.put("405042","TATA Teleservice"); /*India*/
        init.put("405043","TATA Teleservice"); /*India*/
        init.put("405044","TATA Teleservice"); /*India*/
        init.put("405045","TATA Teleservice"); /*India*/
        init.put("405046","TATA Teleservice"); /*India*/
        init.put("405047","TATA Teleservice"); /*India*/
        init.put("405818","Uninor"); /*India*/
        init.put("405819","Uninor"); /*India*/
        init.put("405820","Uninor"); /*India*/
        init.put("405821","Uninor"); /*India*/
        init.put("405822","Uninor"); /*India*/
        init.put("405844","Uninor"); /*India*/
        init.put("405875","Uninor"); /*India*/
        init.put("405880","Uninor"); /*India*/
        init.put("405927","Uninor"); /*India*/
        init.put("405929","Uninor"); /*India*/
        init.put("405824","Videocon Datacom"); /*India*/
        init.put("405827","Videocon Datacom"); /*India*/
        init.put("405834","Videocon Datacom"); /*India*/
        init.put("40420","Vodafone"); /*India*/
        init.put("40446","Vodafone"); /*India*/
        init.put("40405","Vodafone - Gujarat"); /*India*/
        init.put("40401","Vodafone - Haryana"); /*India*/
        init.put("40430","Vodafone - Kolkata"); /*India*/
        init.put("405750","Vodafone IN"); /*India*/
        init.put("405751","Vodafone IN"); /*India*/
        init.put("405752","Vodafone IN"); /*India*/
        init.put("405753","Vodafone IN"); /*India*/
        init.put("405754","Vodafone IN"); /*India*/
        init.put("405755","Vodafone IN"); /*India*/
        init.put("405756","Vodafone IN"); /*India*/
        init.put("51089","3"); /*Indonesia*/
        init.put("51008","AXIS"); /*Indonesia*/
        init.put("51027","Ceria"); /*Indonesia*/
        init.put("51099","Esia"); /*Indonesia*/
        init.put("51028","Fren/Hepi"); /*Indonesia*/
        init.put("51021","IM3"); /*Indonesia*/
        init.put("51001","INDOSAT"); /*Indonesia*/
        init.put("51000","PSN"); /*Indonesia*/
        init.put("51009","SMART"); /*Indonesia*/
        init.put("51003","StarOne"); /*Indonesia*/
        init.put("51007","TelkomFlexi"); /*Indonesia*/
        init.put("51020","TELKOMMobile"); /*Indonesia*/
        init.put("51010","Telkomsel"); /*Indonesia*/
        init.put("51011","XL"); /*Indonesia*/
        init.put("43235","Irancell"); /*Iran*/
        init.put("43293","Iraphone"); /*Iran*/
        init.put("43211","MCI"); /*Iran*/
        init.put("43219","MTCE"); /*Iran*/
        init.put("43232","Taliya"); /*Iran*/
        init.put("43270","TCI"); /*Iran*/
        init.put("43214","TKC"); /*Iran*/
        init.put("41805","Asia Cell"); /*Iraq*/
        init.put("41850","Asia Cell"); /*Iraq*/
        init.put("41840","Korek"); /*Iraq*/
        init.put("41845","Mobitel"); /*Iraq*/
        init.put("41892","Omnnea"); /*Iraq*/
        init.put("41808","SanaTel"); /*Iraq*/
        init.put("41820","Zain IQ"); /*Iraq*/
        init.put("41830","Zain IQ"); /*Iraq*/
        init.put("27205","3"); /*Ireland*/
        init.put("27204","Access Telecom"); /*Ireland*/
        init.put("27209","Clever Communications"); /*Ireland*/
        init.put("27200","E-Mobile"); /*Ireland*/
        init.put("27207","Eircom"); /*Ireland*/
        init.put("27211","Liffey Telecom"); /*Ireland*/
        init.put("27203","Meteor"); /*Ireland*/
        init.put("27202","O2"); /*Ireland*/
        init.put("272020","Tesco Mobile"); /*Ireland*/
        init.put("27201","Vodafone"); /*Ireland*/
        init.put("42502","Cellcom"); /*Israel*/
        init.put("42577","Mirs"); /*Israel*/
        init.put("42501","Orange"); /*Israel*/
        init.put("-","Partner"); /*Israel*/
        init.put("42503","Pelephone"); /*Israel*/
        init.put("22299","3 Italia"); /*Italy*/
        init.put("22298","Blu"); /*Italy*/
        init.put("22202","Elsacom"); /*Italy*/
        init.put("22277","IPSE 2000"); /*Italy*/
        init.put("22207","Noverca"); /*Italy*/
        init.put("22230","RFI"); /*Italy*/
        init.put("22201","TIM"); /*Italy*/
        init.put("22210","Vodafone"); /*Italy*/
        init.put("22288","Wind"); /*Italy*/
        init.put("338020","Cable & Wireless"); /*Jamaica*/
        init.put("338180","Cable & Wireless"); /*Jamaica*/
        init.put("338070","Claro"); /*Jamaica*/
        init.put("338050","Digicel"); /*Jamaica*/
        init.put("44001","DoCoMo"); /*Japan*/
        init.put("44002","DoCoMo"); /*Japan*/
        init.put("44003","DoCoMo"); /*Japan*/
        init.put("44009","DoCoMo"); /*Japan*/
        init.put("44010","DoCoMo"); /*Japan*/
        init.put("44011","DoCoMo"); /*Japan*/
        init.put("44012","DoCoMo"); /*Japan*/
        init.put("44013","DoCoMo"); /*Japan*/
        init.put("44014","DoCoMo"); /*Japan*/
        init.put("44015","DoCoMo"); /*Japan*/
        init.put("44016","DoCoMo"); /*Japan*/
        init.put("44017","DoCoMo"); /*Japan*/
        init.put("44018","DoCoMo"); /*Japan*/
        init.put("44019","DoCoMo"); /*Japan*/
        init.put("44021","DoCoMo"); /*Japan*/
        init.put("44022","DoCoMo"); /*Japan*/
        init.put("44023","DoCoMo"); /*Japan*/
        init.put("44024","DoCoMo"); /*Japan*/
        init.put("44025","DoCoMo"); /*Japan*/
        init.put("44026","DoCoMo"); /*Japan*/
        init.put("44027","DoCoMo"); /*Japan*/
        init.put("44028","DoCoMo"); /*Japan*/
        init.put("44029","DoCoMo"); /*Japan*/
        init.put("44030","DoCoMo"); /*Japan*/
        init.put("44031","DoCoMo"); /*Japan*/
        init.put("44032","DoCoMo"); /*Japan*/
        init.put("44033","DoCoMo"); /*Japan*/
        init.put("44034","DoCoMo"); /*Japan*/
        init.put("44035","DoCoMo"); /*Japan*/
        init.put("44036","DoCoMo"); /*Japan*/
        init.put("44037","DoCoMo"); /*Japan*/
        init.put("44038","DoCoMo"); /*Japan*/
        init.put("44039","DoCoMo"); /*Japan*/
        init.put("44049","DoCoMo"); /*Japan*/
        init.put("44058","DoCoMo"); /*Japan*/
        init.put("44060","DoCoMo"); /*Japan*/
        init.put("44061","DoCoMo"); /*Japan*/
        init.put("44062","DoCoMo"); /*Japan*/
        init.put("44063","DoCoMo"); /*Japan*/
        init.put("44064","DoCoMo"); /*Japan*/
        init.put("44065","DoCoMo"); /*Japan*/
        init.put("44066","DoCoMo"); /*Japan*/
        init.put("44067","DoCoMo"); /*Japan*/
        init.put("44068","DoCoMo"); /*Japan*/
        init.put("44069","DoCoMo"); /*Japan*/
        init.put("44087","DoCoMo"); /*Japan*/
        init.put("44099","DoCoMo"); /*Japan*/
        init.put("44000","eMobile"); /*Japan*/
        init.put("44007","KDDI"); /*Japan*/
        init.put("44008","KDDI"); /*Japan*/
        init.put("44050","KDDI"); /*Japan*/
        init.put("44051","KDDI"); /*Japan*/
        init.put("44052","KDDI"); /*Japan*/
        init.put("44053","KDDI"); /*Japan*/
        init.put("44054","KDDI"); /*Japan*/
        init.put("44055","KDDI"); /*Japan*/
        init.put("44056","KDDI"); /*Japan*/
        init.put("44070","KDDI"); /*Japan*/
        init.put("44071","KDDI"); /*Japan*/
        init.put("44072","KDDI"); /*Japan*/
        init.put("44073","KDDI"); /*Japan*/
        init.put("44074","KDDI"); /*Japan*/
        init.put("44075","KDDI"); /*Japan*/
        init.put("44076","KDDI"); /*Japan*/
        init.put("44077","KDDI"); /*Japan*/
        init.put("44079","KDDI"); /*Japan*/
        init.put("44088","KDDI"); /*Japan*/
        init.put("44089","KDDI"); /*Japan*/
        init.put("44078","Okinawa Cellular Telephone"); /*Japan*/
        init.put("44020","SoftBank"); /*Japan*/
        init.put("44080","TU-KA"); /*Japan*/
        init.put("44081","TU-KA"); /*Japan*/
        init.put("44082","TU-KA"); /*Japan*/
        init.put("44083","TU-KA"); /*Japan*/
        init.put("44084","TU-KA"); /*Japan*/
        init.put("44085","TU-KA"); /*Japan*/
        init.put("44086","TU-KA"); /*Japan*/
        init.put("44004","Vodafone"); /*Japan*/
        init.put("44006","Vodafone"); /*Japan*/
        init.put("44040","Vodafone"); /*Japan*/
        init.put("44041","Vodafone"); /*Japan*/
        init.put("44042","Vodafone"); /*Japan*/
        init.put("44043","Vodafone"); /*Japan*/
        init.put("44044","Vodafone"); /*Japan*/
        init.put("44045","Vodafone"); /*Japan*/
        init.put("44046","Vodafone"); /*Japan*/
        init.put("44047","Vodafone"); /*Japan*/
        init.put("44048","Vodafone"); /*Japan*/
        init.put("44090","Vodafone"); /*Japan*/
        init.put("44092","Vodafone"); /*Japan*/
        init.put("44093","Vodafone"); /*Japan*/
        init.put("44094","Vodafone"); /*Japan*/
        init.put("44095","Vodafone"); /*Japan*/
        init.put("44096","Vodafone"); /*Japan*/
        init.put("44097","Vodafone"); /*Japan*/
        init.put("44098","Vodafone"); /*Japan*/
        init.put("41677","Orange"); /*Jordan*/
        init.put("41603","Umniah"); /*Jordan*/
        init.put("41602","XPress Telecom"); /*Jordan*/
        init.put("41601","Zain"); /*Jordan*/
        init.put("40101","Beeline"); /*Kazakhstan*/
        init.put("40107","Dalacom"); /*Kazakhstan*/
        init.put("40102","K'Cell"); /*Kazakhstan*/
        init.put("40108","Kazakhtelecom"); /*Kazakhstan*/
        init.put("40177","Mobile Telecom Service"); /*Kazakhstan*/
        init.put("63907","Orange Kenya"); /*Kenya*/
        init.put("63902","Safaricom"); /*Kenya*/
        init.put("63905","yu"); /*Kenya*/
        init.put("63903","Zain"); /*Kenya*/
        init.put("54509","Kiribati Frigate"); /*Kiribati*/
        init.put("41904","Viva"); /*Kuwait*/
        init.put("41903","Wataniya"); /*Kuwait*/
        init.put("41902","Zain"); /*Kuwait*/
        init.put("43701","Bitel"); /*Kyrgyzstan*/
        init.put("43703","Fonex"); /*Kyrgyzstan*/
        init.put("43705","MegaCom"); /*Kyrgyzstan*/
        init.put("43709","O!"); /*Kyrgyzstan*/
        init.put("45702","ETL"); /*Laos*/
        init.put("45701","LaoTel"); /*Laos*/
        init.put("45703","LAT"); /*Laos*/
        init.put("45708","Tigo"); /*Laos*/
        init.put("24705","Bite"); /*Latvia*/
        init.put("24709","Camel Mobile"); /*Latvia*/
        init.put("24708","IZZI"); /*Latvia*/
        init.put("24701","LMT"); /*Latvia*/
        init.put("24707","MTS"); /*Latvia*/
        init.put("24706","Rigatta"); /*Latvia*/
        init.put("24702","Tele2"); /*Latvia*/
        init.put("24703","TRIATEL"); /*Latvia*/
        init.put("41501","Alfa"); /*Lebanon*/
        init.put("41503","MTC-Touch"); /*Lebanon*/
        init.put("41505","Ogero Mobile"); /*Lebanon*/
        init.put("65102","Econet Ezin-cel"); /*Lesotho*/
        init.put("65101","Vodacom"); /*Lesotho*/
        init.put("60602","Al-Jeel Phone"); /*Libya*/
        init.put("60606","Hatef Libya"); /*Libya*/
        init.put("60603","Libya Phone"); /*Libya*/
        init.put("60600","Libyana"); /*Libya*/
        init.put("60601","Madar"); /*Libya*/
        init.put("29504","Cubic Telecom"); /*Liechtenstein*/
        init.put("29505","FL1"); /*Liechtenstein*/
        init.put("29502","Orange"); /*Liechtenstein*/
        init.put("29501","Swisscom"); /*Liechtenstein*/
        init.put("29577","Tele 2"); /*Liechtenstein*/
        init.put("24602","BITE"); /*Lithuania*/
        init.put("24605","LitRail"); /*Lithuania*/
        init.put("24606","Mediafon"); /*Lithuania*/
        init.put("24601","Omnitel"); /*Lithuania*/
        init.put("24603","Tele 2"); /*Lithuania*/
        init.put("61807","Cellcom"); /*Livery*/
        init.put("61804","Comium Liberi"); /*Livery*/
        init.put("61802","Libercell"); /*Livery*/
        init.put("61820","LIBTELCO"); /*Livery*/
        init.put("61801","Lonestar Cell"); /*Livery*/
        init.put("27001","LuxGSM"); /*Luksemburg*/
        init.put("27077","Tango"); /*Luksemburg*/
        init.put("27099","Voxmobile"); /*Luksemburg*/
        init.put("45503","3"); /*Macao (People's Republic of China)*/
        init.put("45505","3"); /*Macao (People's Republic of China)*/
        init.put("45502","China Telecom"); /*Macao (People's Republic of China)*/
        init.put("45501","CTM"); /*Macao (People's Republic of China)*/
        init.put("45504","CTM"); /*Macao (People's Republic of China)*/
        init.put("45500","SmarTone"); /*Macao (People's Republic of China)*/
        init.put("64602","Orange"); /*Madagascar*/
        init.put("64603","Sacel"); /*Madagascar*/
        init.put("64604","Telma"); /*Madagascar*/
        init.put("64601","Zain"); /*Madagascar*/
        init.put("65001","TNM"); /*Malawi*/
        init.put("65010","Zain"); /*Malawi*/
        init.put("50201","ATUR 450"); /*Malaysia*/
        init.put("502151","Baraka Telecom Sdn Bhd (MVNE)"); /*Malaysia*/
        init.put("50213","Celcom"); /*Malaysia*/
        init.put("50219","Celcom"); /*Malaysia*/
        init.put("50216","DiGi"); /*Malaysia*/
        init.put("50210","DiGi Telecommunications"); /*Malaysia*/
        init.put("50220","Electcoms Wireless Sdn Bhd"); /*Malaysia*/
        init.put("50212","Maxis"); /*Malaysia*/
        init.put("50217","Maxis"); /*Malaysia*/
        init.put("50214","Telekom Malaysia Berhad for PSTN SMS"); /*Malaysia*/
        init.put("50211","TM Homeline"); /*Malaysia*/
        init.put("502150","Tune Talk Sdn Bhd"); /*Malaysia*/
        init.put("50218","U Mobile"); /*Malaysia*/
        init.put("502152","Yes"); /*Malaysia*/
        init.put("47201","Dhiraagu"); /*Maldives*/
        init.put("47202","Wataniya"); /*Maldives*/
        init.put("61001","Malitel"); /*Mali*/
        init.put("61002","Orange"); /*Mali*/
        init.put("27821","GO"); /*Malta*/
        init.put("27877","Melita"); /*Malta*/
        init.put("27801","Vodafone"); /*Malta*/
        init.put("60902","Chinguitel"); /*Mauretania*/
        init.put("60901","Mattel"); /*Mauretania*/
        init.put("60910","Mauritel"); /*Mauretania*/
        init.put("61710","Emtel"); /*Mauritius*/
        init.put("61702","Mahanagar Telephone (Mauritius) Ltd."); /*Mauritius*/
        init.put("61701","Orange"); /*Mauritius*/
        init.put("334050","Iusacell"); /*Mexico*/
        init.put("33403","movistar"); /*Mexico*/
        init.put("334030","movistar"); /*Mexico*/
        init.put("33401","Nextel"); /*Mexico*/
        init.put("334010","Nextel"); /*Mexico*/
        init.put("33402","Telcel"); /*Mexico*/
        init.put("334020","Telcel"); /*Mexico*/
        init.put("55001","FSM Telecom"); /*Micronesia*/
        init.put("25904","Eventis"); /*Moldova*/
        init.put("25903","IDC"); /*Moldova*/
        init.put("25902","Moldcell"); /*Moldova*/
        init.put("25901","Orange"); /*Moldova*/
        init.put("25905","UnitE"); /*Moldova*/
        init.put("25999","UnitE"); /*Moldova*/
        init.put("21201","Office des Telephones"); /*Monaco*/
        init.put("42898","G.Mobile"); /*Mongolia*/
        init.put("42899","MobiCom"); /*Mongolia*/
        init.put("42891","Skytel"); /*Mongolia*/
        init.put("42888","Unitel"); /*Mongolia*/
        init.put("29703","m:tel CG"); /*Montenegro*/
        init.put("22004","T-Mobile"); /*Montenegro*/
        init.put("29702","T-Mobile"); /*Montenegro*/
        init.put("29704","T-Mobile"); /*Montenegro*/
        init.put("29701","Telenor Montenegro"); /*Montenegro*/
        init.put("60401","IAM"); /*Morocco*/
        init.put("60405","INWI"); /*Morocco*/
        init.put("60400","Meditel"); /*Morocco*/
        init.put("64301","mCel"); /*Mozambique*/
        init.put("64304","Vodacom"); /*Mozambique*/
        init.put("41401","MPT"); /*Myanmar*/
        init.put("64903","Cell One"); /*Namibia*/
        init.put("64901","MTC"); /*Namibia*/
        init.put("64902","switch"); /*Namibia*/
        init.put("53602","Digicel"); /*Nauru*/
        init.put("42902","Mero Mobile"); /*Nepal*/
        init.put("42901","Nepal Telecom"); /*Nepal*/
        init.put("42904","SmartCell"); /*Nepal*/
        init.put("42903","United Telecom Limited"); /*Nepal*/
        init.put("36294","Bayus"); /*Netherlands Antilles (Netherlands)*/
        init.put("36269","Digicel"); /*Netherlands Antilles (Netherlands)*/
        init.put("36295","MIO"); /*Netherlands Antilles (Netherlands)*/
        init.put("36251","Telcell"); /*Netherlands Antilles (Netherlands)*/
        init.put("36291","UTS"); /*Netherlands Antilles (Netherlands)*/
        init.put("54601","Mobilis"); /*New Caledonia (France)*/
        init.put("53024","NZ Comms"); /*New Zealand*/
        init.put("53000","Telecom"); /*New Zealand*/
        init.put("53002","Telecom"); /*New Zealand*/
        init.put("53005","Telecom"); /*New Zealand*/
        init.put("53004","TelstraClear"); /*New Zealand*/
        init.put("53001","Vodafone"); /*New Zealand*/
        init.put("53003","Woosh"); /*New Zealand*/
        init.put("71021","Claro"); /*Nicaragua*/
        init.put("71030","movistar"); /*Nicaragua*/
        init.put("71073","SERCOM"); /*Nicaragua*/
        init.put("61404","Orange"); /*Niger*/
        init.put("61401","SahelCom"); /*Niger*/
        init.put("61403","Telecel"); /*Niger*/
        init.put("61402","Zain"); /*Niger*/
        init.put("62160","Etisalat"); /*Nigeria*/
        init.put("62150","Glo"); /*Nigeria*/
        init.put("62140","M-Tel"); /*Nigeria*/
        init.put("62130","MTN"); /*Nigeria*/
        init.put("62125","Visafone"); /*Nigeria*/
        init.put("62120","Zain"); /*Nigeria*/
        init.put("55501","Telecom Niue"); /*Niue*/
        init.put("467192","Koryolink"); /*North Korea*/
        init.put("467193","SUN NET"); /*North Korea*/
        init.put("24209","Barablu Mobile Norway Ltd"); /*Norway*/
        init.put("24206","Ice"); /*Norway*/
        init.put("24220","Jernbaneverket AS"); /*Norway*/
        init.put("24223","Lyca"); /*Norway*/
        init.put("24203","MTU"); /*Norway*/
        init.put("24202","NetCom"); /*Norway*/
        init.put("24205","Network Norway"); /*Norway*/
        init.put("24211","SystemNet"); /*Norway*/
        init.put("24208","TDC Mobil AS"); /*Norway*/
        init.put("24204","Tele2"); /*Norway*/
        init.put("24201","Telenor"); /*Norway*/
        init.put("--","Telia"); /*Norway*/
        init.put("24207","Ventelo"); /*Norway*/
        init.put("42203","Nawras"); /*Oman*/
        init.put("42202","Oman Mobile"); /*Oman*/
        init.put("25030","Megafon"); /*Osetia*/
        init.put("41008","Instaphone"); /*Pakistan*/
        init.put("41001","Mobilink"); /*Pakistan*/
        init.put("41006","Telenor"); /*Pakistan*/
        init.put("41003","Ufone"); /*Pakistan*/
        init.put("41007","Warid"); /*Pakistan*/
        init.put("41004","Zong"); /*Pakistan*/
        init.put("55280","Palau Mobile"); /*Palau*/
        init.put("55201","PNCC"); /*Palau*/
        init.put("42505","JAWWAL"); /*Palestine*/
        init.put("42506","Wataniya"); /*Palestine*/
        init.put("71401","Cable & Wireless"); /*Panama*/
        init.put("71404","Digicel"); /*Panama*/
        init.put("71403","laro"); /*Panama*/
        init.put("71402","movistar"); /*Panama*/
        init.put("53701","B-Mobile"); /*Papua New Guinea*/
        init.put("53703","Digicel"); /*Papua New Guinea*/
        init.put("74402","Claro"); /*Paraguay*/
        init.put("74406","Copaco"); /*Paraguay*/
        init.put("74405","Personal"); /*Paraguay*/
        init.put("74404","Tigo"); /*Paraguay*/
        init.put("74401","VOX"); /*Paraguay*/
        init.put("71610","Claro"); /*Peru*/
        init.put("71606","movistar"); /*Peru*/
        init.put("71607","NEXTEL"); /*Peru*/
        init.put("51511","ACeS Philippines"); /*Philippines*/
        init.put("51505","Digitel"); /*Philippines*/
        init.put("51502","Globe"); /*Philippines*/
        init.put("51501","Islacom"); /*Philippines*/
        init.put("51588","Nextel"); /*Philippines*/
        init.put("51518","Red Mobile"); /*Philippines*/
        init.put("51503","Smart Gold"); /*Philippines*/
        init.put("26017","Aero2"); /*Poland*/
        init.put("26015","CenterNet"); /*Poland*/
        init.put("26012","Cyfrowy Polsat"); /*Poland*/
        init.put("26008","E-Telko"); /*Poland*/
        init.put("26016","Mobyland"); /*Poland*/
        init.put("26011","Nordisk Polska"); /*Poland*/
        init.put("26003","Orange"); /*Poland*/
        init.put("26006","Play"); /*Poland*/
        init.put("26001","Plus"); /*Poland*/
        init.put("26005","Polska Telefonia"); /*Poland*/
        init.put("26007","Premium Internet"); /*Poland*/
        init.put("26013","Sferia"); /*Poland*/
        init.put("26002","T-mobile"); /*Poland*/
        init.put("26004","Tele2"); /*Poland*/
        init.put("26010","Telefony Opalenickie"); /*Poland*/
        init.put("26009","Telekomunikacja Kolejowa"); /*Poland*/
        init.put("26803","Optimus"); /*Portugal*/
        init.put("26806","TMN"); /*Portugal*/
        init.put("26801","Vodafone"); /*Portugal*/
        init.put("26821","Zapp"); /*Portugal*/
        init.put("33011","Claro"); /*Puerto Rico*/
        init.put("330110","Claro"); /*Puerto Rico*/
        init.put("33000","Open Mobile"); /*Puerto Rico*/
        init.put("42705","Ministry of Interior"); /*Qatar*/
        init.put("42701","Qatarnet"); /*Qatar*/
        init.put("42702","Vodafone"); /*Qatar*/
        init.put("62910","Libertis Telecom"); /*Republic of Congo*/
        init.put("62907","Warid Telecom"); /*Republic of Congo*/
        init.put("62901","Zain"); /*Republic of Congo*/
        init.put("29402","Cosmofon"); /*Republic of Macedonia*/
        init.put("29401","T-Mobile"); /*Republic of Macedonia*/
        init.put("29403","VIP"); /*Republic of Macedonia*/
        init.put("64700","Orange"); /*Reunion (France)*/
        init.put("64702","Outremer"); /*Reunion (France)*/
        init.put("64710","SFR Reunion"); /*Reunion (France)*/
        init.put("22603","Cosmote"); /*Romania*/
        init.put("22605","DIGI.mobil"); /*Romania*/
        init.put("22611","Enigma-System"); /*Romania*/
        init.put("22610","Orange"); /*Romania*/
        init.put("22602","Romtelecom"); /*Romania*/
        init.put("22601","Vodafone"); /*Romania*/
        init.put("22604","Zapp"); /*Romania*/
        init.put("22606","Zapp"); /*Romania*/
        init.put("25012","Baykalwestcom"); /*Russian Federation*/
        init.put("25028","Beeline"); /*Russian Federation*/
        init.put("25099","Beeline"); /*Russian Federation*/
        init.put("25010","DTC"); /*Russian Federation*/
        init.put("25005","ETK"); /*Russian Federation*/
        init.put("25019","INDIGO"); /*Russian Federation*/
        init.put("25013","KUGSM"); /*Russian Federation*/
        init.put("25002","MegaFon"); /*Russian Federation*/
        init.put("25023","Mobicom - Novosibirsk"); /*Russian Federation*/
        init.put("25035","MOTIV"); /*Russian Federation*/
        init.put("25001","MTS"); /*Russian Federation*/
        init.put("25003","NCC"); /*Russian Federation*/
        init.put("25016","NTC"); /*Russian Federation*/
        init.put("25011","Orensot"); /*Russian Federation*/
        init.put("25092","Primtelefon"); /*Russian Federation*/
        init.put("25004","Sibchallenge"); /*Russian Federation*/
        init.put("25006","Skylink"); /*Russian Federation*/
        init.put("25009","Skylink"); /*Russian Federation*/
        init.put("25007","SMARTS"); /*Russian Federation*/
        init.put("25014","SMARTS"); /*Russian Federation*/
        init.put("25015","SMARTS"); /*Russian Federation*/
        init.put("25044","Stavtelesot / North Caucasian GSM"); /*Russian Federation*/
        init.put("25038","Tambov GSM"); /*Russian Federation*/
        init.put("25020","Tele2"); /*Russian Federation*/
        init.put("25093","Telecom XXI"); /*Russian Federation*/
        init.put("25017","Utel"); /*Russian Federation*/
        init.put("25039","Utel"); /*Russian Federation*/
        init.put("63510","MTN"); /*Rwanda*/
        init.put("63512","Rwandatel"); /*Rwanda*/
        init.put("63513","Tigo"); /*Rwanda*/
        init.put("356110","Cable & Wireless"); /*Saint Kitts and Nevis*/
        init.put("356070","Chippie"); /*Saint Kitts and Nevis*/
        init.put("356050","Digicel"); /*Saint Kitts and Nevis*/
        init.put("358110","Cable & Wireless"); /*Saint Lucia*/
        init.put("358050","Digicel"); /*Saint Lucia*/
        init.put("360110","Cable & Wireless"); /*Saint Vincent and the Grenadines*/
        init.put("360100","Cingular Wireless"); /*Saint Vincent and the Grenadines*/
        init.put("360050","Digicel"); /*Saint Vincent and the Grenadines*/
        init.put("360070","Digicel"); /*Saint Vincent and the Grenadines*/
        init.put("30801","Ameris"); /*Saint-Pierre and Miquelon (France)*/
        init.put("54901","Digicel"); /*Samoa*/
        init.put("54927","SamoaTel"); /*Samoa*/
        init.put("29201","PRIMA"); /*San Marino*/
        init.put("62601","CSTmovel"); /*Sao Tome and Principe*/
        init.put("42007","EAE"); /*Saudi Arabia*/
        init.put("42003","Mobily"); /*Saudi Arabia*/
        init.put("42001","STC"); /*Saudi Arabia*/
        init.put("42004","Zain SA"); /*Saudi Arabia*/
        init.put("60803","Expresso"); /*Senegal*/
        init.put("60802","Sentel GSM"); /*Senegal*/
        init.put("60801","Sonatel ALIZE"); /*Senegal*/
        init.put("22003","Telekom Srbija"); /*Serbia*/
        init.put("22001","Telenor"); /*Serbia*/
        init.put("22005","VIP Mobile"); /*Serbia*/
        init.put("63301","Cable & Wireless (Seychelles) Ltd."); /*Seychelles*/
        init.put("63302","Mediatech International"); /*Seychelles*/
        init.put("63310","Telecom Airtel"); /*Seychelles*/
        init.put("61905","Africell"); /*Sierra Leone*/
        init.put("61904","Comium"); /*Sierra Leone*/
        init.put("61903","Datatel"); /*Sierra Leone*/
        init.put("61902","Millicom"); /*Sierra Leone*/
        init.put("61925","Mobitel"); /*Sierra Leone*/
        init.put("61901","Zain"); /*Sierra Leone*/
        init.put("52512","Digital Trunked Radio Network"); /*Singapore*/
        init.put("52503","M1"); /*Singapore*/
        init.put("52501","SingTel"); /*Singapore*/
        init.put("52502","SingTel-G18"); /*Singapore*/
        init.put("52505","StarHub"); /*Singapore*/
        init.put("23105","Mobile Entertainment Company"); /*Slovakia*/
        init.put("23106","O2"); /*Slovakia*/
        init.put("23101","Orange"); /*Slovakia*/
        init.put("23102","T-Mobile"); /*Slovakia*/
        init.put("23104","T-Mobile"); /*Slovakia*/
        init.put("23103","Unient Communications"); /*Slovakia*/
        init.put("23199","eSR"); /*Slovakia*/
        init.put("29341","Mobitel"); /*Slovenia*/
        init.put("29340","SI.mobil - Vodafone"); /*Slovenia*/
        init.put("29364","T-2"); /*Slovenia*/
        init.put("29370","Tusmobil"); /*Slovenia*/
        init.put("54001","BREEZE"); /*Solomon Islands*/
        init.put("5401","BREEZE"); /*Solomon Islands*/
        init.put("63730","Golis"); /*Somalia*/
        init.put("63725","Hormuud"); /*Somalia*/
        init.put("63710","Nationlink"); /*Somalia*/
        init.put("63760","Nationlink Telecom"); /*Somalia*/
        init.put("63704","Somafone"); /*Somalia*/
        init.put("638","Telcom Mobile"); /*Somalia*/
        init.put("63701","Telesom"); /*Somalia*/
        init.put("63782","Telesom"); /*Somalia*/
        init.put("65530","Bokamoso Consortium"); /*South Africa*/
        init.put("65521","Cape Town Metropolitan Council"); /*South Africa*/
        init.put("65507","Cell C"); /*South Africa*/
        init.put("65532","Ilizwi Telecommunications"); /*South Africa*/
        init.put("65531","Karabo Telecoms (Pty) Ltd."); /*South Africa*/
        init.put("65510","MTN"); /*South Africa*/
        init.put("65513","Neotel"); /*South Africa*/
        init.put("65511","SAPS Gauteng"); /*South Africa*/
        init.put("65506","Sentech"); /*South Africa*/
        init.put("65502","Telkom Mobile / 8.ta"); /*South Africa*/
        init.put("65533","Thinta Thinta Telecommunications"); /*South Africa*/
        init.put("65501","Vodacom"); /*South Africa*/
        init.put("45004","KT"); /*South Korea*/
        init.put("45008","KTF"); /*South Korea*/
        init.put("45002","KTF CDMA"); /*South Korea*/
        init.put("45006","LGU+"); /*South Korea*/
        init.put("45003","Power 017"); /*South Korea*/
        init.put("45005","SK Telecom"); /*South Korea*/
        init.put("21423","BARABLU"); /*Spain*/
        init.put("21415","BT"); /*Spain*/
        init.put("21422","DigiMobil"); /*Spain*/
        init.put("21424","Eroski"); /*Spain*/
        init.put("21408","Euskaltel"); /*Spain*/
        init.put("21420","Fonyou"); /*Spain*/
        init.put("21425","LycaMobile"); /*Spain*/
        init.put("21407","movistar"); /*Spain*/
        init.put("21417","MUbil R"); /*Spain*/
        init.put("21418","ONO"); /*Spain*/
        init.put("21403","Orange"); /*Spain*/
        init.put("21409","Orange"); /*Spain*/
        init.put("21419","Simyo"); /*Spain*/
        init.put("21416","TeleCable"); /*Spain*/
        init.put("21405","TME"); /*Spain*/
        init.put("21401","Vodafone"); /*Spain*/
        init.put("21406","Vodafone"); /*Spain*/
        init.put("21404","Yoigo"); /*Spain*/
        init.put("41305","Airtel"); /*Sri Lanka*/
        init.put("41302","Dialog"); /*Sri Lanka*/
        init.put("41308","Hutch Sri Lanka"); /*Sri Lanka*/
        init.put("41301","Mobitel"); /*Sri Lanka*/
        init.put("41303","Tigo"); /*Sri Lanka*/
        init.put("63401","Mobitel / Mobile Telephone Company"); /*Sudan*/
        init.put("63402","MTN"); /*Sudan*/
        init.put("63407","Sudani One"); /*Sudan*/
        init.put("63405","Vivacell"); /*Sudan*/
        init.put("74603","Digicel"); /*Suriname*/
        init.put("74602","Telesu"); /*Suriname*/
        init.put("74604","Uniqa"); /*Suriname*/
        init.put("65310","Swazi MTN"); /*Swaziland*/
        init.put("24002","3 HUTCHISON"); /*Sweden*/
        init.put("24004","3G Infrastructure Services"); /*Sweden*/
        init.put("24016","42IT"); /*Sweden*/
        init.put("24021","Banverket"); /*Sweden*/
        init.put("24012","Barablu Mobile Scandinavia"); /*Sweden*/
        init.put("24026","Beepsend"); /*Sweden*/
        init.put("24025","DigiTelMobile"); /*Sweden*/
        init.put("24017","Gotanet"); /*Sweden*/
        init.put("24000","Halebop"); /*Sweden*/
        init.put("24011","Lindholmen Science Park"); /*Sweden*/
        init.put("24033","Mobile Arts AB"); /*Sweden*/
        init.put("24003","Nordisk Mobiltelefon"); /*Sweden*/
        init.put("24010","SpringMobil"); /*Sweden*/
        init.put("24024","Sweden 2G"); /*Sweden*/
        init.put("24024","Sweden 2G"); /*Sweden*/
        init.put("24005","Sweden 3G"); /*Sweden*/
        init.put("24014","TDC Mobil"); /*Sweden*/
        init.put("24007","Tele2Comviq"); /*Sweden*/
        init.put("24006","Telenor"); /*Sweden*/
        init.put("24008","Telenor"); /*Sweden*/
        init.put("24009","Telenor Mobile Sverige"); /*Sweden*/
        init.put("24001","TeliaSonera Mobile Networks"); /*Sweden*/
        init.put("24013","Ventelo Sverige"); /*Sweden*/
        init.put("24020","Wireless Maingate"); /*Sweden*/
        init.put("24015","Wireless Maingate Nordic"); /*Sweden*/
        init.put("22850","3G Mobile AG"); /*Switzerland*/
        init.put("22851","BebbiCell AG"); /*Switzerland*/
        init.put("22807","IN&Phone"); /*Switzerland*/
        init.put("22803","Orange"); /*Switzerland*/
        init.put("22806","SBB AG"); /*Switzerland*/
        init.put("22802","Sunrise"); /*Switzerland*/
        init.put("22801","Swisscom"); /*Switzerland*/
        init.put("22808","Tele2"); /*Switzerland*/
        init.put("22805","Togewanet AG (Comfone)"); /*Switzerland*/
        init.put("41702","MTN Syria"); /*Syria*/
        init.put("41701","SyriaTel"); /*Syria*/
        init.put("46602","APTG"); /*Taiwan*/
        init.put("46605","APTG"); /*Taiwan*/
        init.put("46611","Chunghwa LDM"); /*Taiwan*/
        init.put("46692","Chungwa"); /*Taiwan*/
        init.put("46601","FarEasTone"); /*Taiwan*/
        init.put("46688","KG Telecom"); /*Taiwan*/
        init.put("46693","MobiTai"); /*Taiwan*/
        init.put("46697","Taiwan Mobile"); /*Taiwan*/
        init.put("46699","TransAsia"); /*Taiwan*/
        init.put("46606","Tuntex"); /*Taiwan*/
        init.put("46689","VIBO"); /*Taiwan*/
        init.put("43604","Babilon-M"); /*Tajikistan*/
        init.put("43605","CTJTHSC Tajik-tel"); /*Tajikistan*/
        init.put("43602","Indigo"); /*Tajikistan*/
        init.put("43603","MLT"); /*Tajikistan*/
        init.put("43601","Somoncom"); /*Tajikistan*/
        init.put("43612","Tcell"); /*Tajikistan*/
        init.put("64009","Hits"); /*Tanzania*/
        init.put("64002","Mobitel"); /*Tanzania*/
        init.put("64006","Sasatel"); /*Tanzania*/
        init.put("64011","SmileCom"); /*Tanzania*/
        init.put("64001","Tritel"); /*Tanzania*/
        init.put("64007","TTCL Mobile"); /*Tanzania*/
        init.put("64008","TTCL Mobile"); /*Tanzania*/
        init.put("64004","Vodacom"); /*Tanzania*/
        init.put("64005","Zain"); /*Tanzania*/
        init.put("64003","Zantel"); /*Tanzania*/
        init.put("52015","ACT Mobile"); /*Thailand*/
        init.put("52001","Advanced Info Service"); /*Thailand*/
        init.put("52023","Advanced Info Service"); /*Thailand*/
        init.put("52000","CAT CDMA"); /*Thailand*/
        init.put("52002","CAT CDMA"); /*Thailand*/
        init.put("52018","DTAC"); /*Thailand*/
        init.put("52099","True Move"); /*Thailand*/
        init.put("52010","WCS IQ"); /*Thailand*/
        init.put("61503","Moov"); /*Togo*/
        init.put("61505","Telecel"); /*Togo*/
        init.put("61501","Togo Cell"); /*Togo*/
        init.put("53988","Digicel"); /*Tonga*/
        init.put("53943","Shoreline Communication"); /*Tonga*/
        init.put("53901","Tonga Communications Corporation"); /*Tonga*/
        init.put("37412","bmobile"); /*Trinidad and Tobago*/
        init.put("37413","Digicel"); /*Trinidad and Tobago*/
        init.put("374130","Digicel"); /*Trinidad and Tobago*/
        init.put("60501","Orange"); /*Tunisia*/
        init.put("60502","Tunicell"); /*Tunisia*/
        init.put("60503","Tunisiana"); /*Tunisia*/
        init.put("28603","Avea"); /*Turkey*/
        init.put("28604","Aycell"); /*Turkey*/
        init.put("28601","Turkcell"); /*Turkey*/
        init.put("28602","Vodafone"); /*Turkey*/
        init.put("43801","MTS"); /*Turkmenistan*/
        init.put("43802","TM-Cell"); /*Turkmenistan*/
        init.put("55301","TTC"); /*Tuvalu*/
        init.put("64110","MTN"); /*Uganda*/
        init.put("64114","Orange"); /*Uganda*/
        init.put("64111","Uganda Telecom Ltd."); /*Uganda*/
        init.put("64122","Warid Telecom"); /*Uganda*/
        init.put("64101","Zain"); /*Uganda*/
        init.put("25502","Beeline"); /*Ukraine*/
        init.put("25523","CDMA Ukraine"); /*Ukraine*/
        init.put("25505","Golden Telecom"); /*Ukraine*/
        init.put("25504","IT"); /*Ukraine*/
        init.put("25503","Kyivstar"); /*Ukraine*/
        init.put("25506","life:)"); /*Ukraine*/
        init.put("25501","MTS"); /*Ukraine*/
        init.put("25521","PEOPLEnet"); /*Ukraine*/
        init.put("25507","Utel"); /*Ukraine*/
        init.put("42403","du"); /*United Arab Emirates*/
        init.put("42402","Etisalat"); /*United Arab Emirates*/
        init.put("23420","3 Hutchison"); /*United Kingdom*/
        init.put("23400","BT"); /*United Kingdom*/
        init.put("23455","Cable & Wireless / Sure Mobile (Isle of Man)"); /*United Kingdom*/
        init.put("23418","Cloud9"); /*United Kingdom*/
        init.put("23403","Jersey Telenet"); /*United Kingdom*/
        init.put("23450","JT-Wave"); /*United Kingdom*/
        init.put("23458","Manx Telecom"); /*United Kingdom*/
        init.put("23401","MCom"); /*United Kingdom*/
        init.put("23402","O2"); /*United Kingdom*/
        init.put("23410","O2"); /*United Kingdom*/
        init.put("23411","O2"); /*United Kingdom*/
        init.put("23433","Orange"); /*United Kingdom*/
        init.put("23434","Orange"); /*United Kingdom*/
        init.put("23412","Railtrack"); /*United Kingdom*/
        init.put("23422","Routo Telecom"); /*United Kingdom*/
        init.put("23409","Sure Mobile"); /*United Kingdom*/
        init.put("23430","T-Mobile"); /*United Kingdom*/
        init.put("23419","Telaware"); /*United Kingdom*/
        init.put("234100","Tesco Mobile"); /*United Kingdom*/
        init.put("23477","Unknown"); /*United Kingdom*/
        init.put("23431","Virgin"); /*United Kingdom*/
        init.put("23432","Virgin"); /*United Kingdom*/
        init.put("23415","Vodafone"); /*United Kingdom*/
        init.put("310880","Advantage"); /*United States*/
        init.put("310850","Aeris"); /*United States*/
        init.put("310640","Airadigm"); /*United States*/
        init.put("310780","Airlink PCS"); /*United States*/
        init.put("310034","Airpeak"); /*United States*/
        init.put("310510","Airtel"); /*United States*/
        init.put("310430","Alaska Digitel"); /*United States*/
        init.put("310500","Alltel"); /*United States*/
        init.put("310590","Alltel"); /*United States*/
        init.put("310630","AmeriLink PCS"); /*United States*/
        init.put("310038","AT&T"); /*United States*/
        init.put("310090","AT&T"); /*United States*/
        init.put("310150","AT&T"); /*United States*/
        init.put("310170","AT&T"); /*United States*/
        init.put("310410","AT&T"); /*United States*/
        init.put("310560","AT&T"); /*United States*/
        init.put("310680","AT&T"); /*United States*/
        init.put("310380","AT&T Mobility"); /*United States*/
        init.put("310980","AT&T Mobility"); /*United States*/
        init.put("310990","AT&T Mobility"); /*United States*/
        init.put("310830","Caprock"); /*United States*/
        init.put("310350","Carolina Phone"); /*United States*/
        init.put("311130","Cell One Amarillo"); /*United States*/
        init.put("310320","Cellular One"); /*United States*/
        init.put("310440","Cellular One"); /*United States*/
        init.put("310390","Cellular One of East Texas"); /*United States*/
        init.put("311190","Cellular Properties"); /*United States*/
        init.put("310030","Centennial"); /*United States*/
        init.put("311010","Chariton Valley"); /*United States*/
        init.put("310570","Chinook Wireless"); /*United States*/
        init.put("310480","Choice Phone"); /*United States*/
        init.put("311120","Choice Phone"); /*United States*/
        init.put("310420","Cincinnati Bell"); /*United States*/
        init.put("311180","Cingular Wireless"); /*United States*/
        init.put("310620","Coleman County Telecom"); /*United States*/
        init.put("311040","Commnet Wireless"); /*United States*/
        init.put("310040","Concho"); /*United States*/
        init.put("310690","Conestoga"); /*United States*/
        init.put("310060","Consolidated Telcom"); /*United States*/
        init.put("310740","Convey"); /*United States*/
        init.put("310080","Corr"); /*United States*/
        init.put("310016","Cricket Communications"); /*United States*/
        init.put("310940","Digital Cellular"); /*United States*/
        init.put("310190","Dutch Harbor"); /*United States*/
        init.put("311070","Easterbrooke"); /*United States*/
        init.put("311160","Endless Mountains Wireless"); /*United States*/
        init.put("310610","Epic Touch"); /*United States*/
        init.put("311060","Farmers Cellular"); /*United States*/
        init.put("311210","Farmers Cellular"); /*United States*/
        init.put("310311","Farmers Wireless"); /*United States*/
        init.put("310910","First Cellular"); /*United States*/
        init.put("310300","Get Mobile Inc"); /*United States*/
        init.put("310970","Globalstar"); /*United States*/
        init.put("311100","High Plains Wireless"); /*United States*/
        init.put("311110","High Plains Wireless"); /*United States*/
        init.put("310070","Highland Cellular"); /*United States*/
        init.put("310400","i CAN_GSM"); /*United States*/
        init.put("310770","i wireless"); /*United States*/
        init.put("311030","Indigo Wireless"); /*United States*/
        init.put("310650","Jasper"); /*United States*/
        init.put("311090","Long Lines Wireless"); /*United States*/
        init.put("310010","MCI"); /*United States*/
        init.put("310000","Mid-Tex Cellular"); /*United States*/
        init.put("311000","Mid-Tex Cellular"); /*United States*/
        init.put("311020","Missouri RSA 5 Partnership"); /*United States*/
        init.put("310013","MobileTel"); /*United States*/
        init.put("316010","Nextel"); /*United States*/
        init.put("310017","North Sight Communications Inc."); /*United States*/
        init.put("310670","Northstar"); /*United States*/
        init.put("310540","Oklahoma Western"); /*United States*/
        init.put("310870","PACE"); /*United States*/
        init.put("310760","Panhandle"); /*United States*/
        init.put("311170","PetroCom"); /*United States*/
        init.put("311080","Pine Cellular"); /*United States*/
        init.put("310790","PinPoint"); /*United States*/
        init.put("310100","Plateau Wireless"); /*United States*/
        init.put("310960","Plateau Wireless"); /*United States*/
        init.put("310110","PTI Pacifica"); /*United States*/
        init.put("310730","SeaMobile"); /*United States*/
        init.put("310046","SIMMETRY"); /*United States*/
        init.put("310460","Simmetry"); /*United States*/
        init.put("316011","Southern Communications Services"); /*United States*/
        init.put("310120","Sprint"); /*United States*/
        init.put("311140","Sprocket"); /*United States*/
        init.put("310490","SunCom"); /*United States*/
        init.put("310026","T-Mobile"); /*United States*/
        init.put("310160","T-Mobile"); /*United States*/
        init.put("310200","T-Mobile"); /*United States*/
        init.put("310210","T-Mobile"); /*United States*/
        init.put("310220","T-Mobile"); /*United States*/
        init.put("310230","T-Mobile"); /*United States*/
        init.put("310240","T-Mobile"); /*United States*/
        init.put("310250","T-Mobile"); /*United States*/
        init.put("310260","T-Mobile"); /*United States*/
        init.put("310270","T-Mobile"); /*United States*/
        init.put("310280","T-Mobile"); /*United States*/
        init.put("310290","T-Mobile"); /*United States*/
        init.put("310310","T-Mobile"); /*United States*/
        init.put("310330","T-Mobile"); /*United States*/
        init.put("310580","T-Mobile"); /*United States*/
        init.put("310660","T-Mobile"); /*United States*/
        init.put("310800","T-Mobile"); /*United States*/
        init.put("310900","Taylor"); /*United States*/
        init.put("310014","Testing"); /*United States*/
        init.put("310020","Union Telephone Company"); /*United States*/
        init.put("310520","VeriSign"); /*United States*/
        init.put("20404","Verizon"); /*United States*/
        init.put("246081","Verizon"); /*United States*/
        init.put("310004","Verizon"); /*United States*/
        init.put("310012","Verizon"); /*United States*/
        init.put("311480","Verizon"); /*United States*/
        init.put("310450","Viaero"); /*United States*/
        init.put("310180","West Central"); /*United States*/
        init.put("310530","West Virginia Wireless"); /*United States*/
        init.put("310340","Westlink"); /*United States*/
        init.put("311050","Wikes Cellular"); /*United States*/
        init.put("311150","Wilkes Cellular"); /*United States*/
        init.put("310890","Wireless Alliance"); /*United States*/
        init.put("310950","XIT Wireless"); /*United States*/
        init.put("74800","Ancel"); /*Uruguay*/
        init.put("74801","Ancel"); /*Uruguay*/
        init.put("74810","Claro"); /*Uruguay*/
        init.put("74807","Movistar"); /*Uruguay*/
        init.put("43404","Beeline"); /*Uzbekistan*/
        init.put("43401","Buztel"); /*Uzbekistan*/
        init.put("43407","MTS"); /*Uzbekistan*/
        init.put("43406","Perfectum Mobile"); /*Uzbekistan*/
        init.put("43405","Ucell"); /*Uzbekistan*/
        init.put("43402","Uzmacom"); /*Uzbekistan*/
        init.put("54101","SMILE"); /*Vanuatu*/
        init.put("73401","Digitel"); /*Venezuela*/
        init.put("73402","Digitel"); /*Venezuela*/
        init.put("73403","Digitel"); /*Venezuela*/
        init.put("73406","Movilnet"); /*Venezuela*/
        init.put("73404","movistar"); /*Venezuela*/
        init.put("45208","3G EVNTelecom"); /*Vietnam*/
        init.put("45207","Beeline VN"); /*Vietnam*/
        init.put("45206","E-Mobile"); /*Vietnam*/
        init.put("45205","HT Mobile"); /*Vietnam*/
        init.put("45201","MobiFone"); /*Vietnam*/
        init.put("45203","S-Fone"); /*Vietnam*/
        init.put("45204","Viettel Mobile"); /*Vietnam*/
        init.put("45202","Vinaphone"); /*Vietnam*/
        init.put("376350","C&W"); /*Wyspy Turks i Caicos*/
        init.put("33805","Digicel"); /*Wyspy Turks i Caicos*/
        init.put("376352","Islandcom"); /*Wyspy Turks i Caicos*/
        init.put("42104","HiTS-UNITEL"); /*Yemen*/
        init.put("42102","MTN"); /*Yemen*/
        init.put("42101","SabaFon"); /*Yemen*/
        init.put("42103","Yemen Mobile"); /*Yemen*/
        init.put("64502","MTN"); /*Zambia*/
        init.put("64501","Zain"); /*Zambia*/
        init.put("64503","ZAMTEL"); /*Zambia*/
        init.put("64804","Econet"); /*Zimbabwe*/
        init.put("64801","Net*One"); /*Zimbabwe*/
        init.put("64803","Telecel"); /*Zimbabwe*/

        operators = Collections.unmodifiableMap(init);
    }

    public static String operatorReplace(String response){
        if(operators == null){
            initList();
        }
        return operators.containsKey(response) ? operators.get(response) : response;
    }
}
