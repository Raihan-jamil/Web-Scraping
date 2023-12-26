package current_scripts

import com.rdc.importer.scrapian.ScrapianContext
import com.rdc.importer.scrapian.model.StringSource
import com.rdc.importer.scrapian.util.ModuleLoader
import com.rdc.scrape.ScrapeAddress
import com.rdc.scrape.ScrapeEvent
import org.openqa.selenium.*
import org.openqa.selenium.remote.RemoteWebDriver
import java.util.regex.Pattern

context.setup([connectionTimeout: 25000, socketTimeout: 25000, retryCount: 10, multithread: true, userAgent: "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_13_6) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/83.0.4103.61 Safari/537.36"])
context.session.encoding = "UTF-8"; //change it according to web page's encoding
context.session.escape = true;

Us_mt_csio_legal_actions script = new Us_mt_csio_legal_actions(context)
script.initParsing()

class Us_mt_csio_legal_actions {
    final ScrapianContext context
    RemoteWebDriver driver
    HashMap<String,Object> chromePrefs = [:]
    List<String> options

    def moduleLoaderVersion
    final def moduleFactory
    final addressParser
    final entityType

    def final mainUrl = "https://csimt.gov/legal-actions/"
    final def eventDes = "This entity appears on the United Kingdom Health and Safety Executive Breach list."

    Us_mt_csio_legal_actions(context) {
        this.context = context
        moduleLoaderVersion = context.scriptParams.moduleLoaderVersion
        moduleFactory = ModuleLoader.getFactory(moduleLoaderVersion)
        entityType = moduleFactory.getEntityTypeDetection(context)
        addressParser = moduleFactory.getGenericAddressParser(context)
        driver = createWebDriver()
    }

    def initParsing() {
        try{
            context.info("Invoking $mainUrl")
            driver.get(mainUrl)
            Thread.sleep(5000)
            def html = driver.getPageSource()
            def table1noMatcher = html =~ /table_1.+?7" tabindex="0">(\d{2,})<\/a><\/span>/
            def table1end, table2end
            if (table1noMatcher.find()) {
                table1end = table1noMatcher.group(1)
                table1end = Integer.parseInt(table1end)
            }

            def table2noMatcher = html =~ /table_2.+?7" tabindex="0">(\d{2,})<\\/a><\\/span>/
            if (table2noMatcher.find()) {
                table2end = table2noMatcher.group(1)
                table2end = Integer.parseInt(table2end)
            }
            getData(html)
            getData02(html)
            getNextPage(mainUrl, table1end, table2end)
            driver.quit();
        } catch (Exception e) {
            driver.quit();
            throw e;
        }
    }

    def createWebDriver() {
        options = ["--disable-gpu",
                   "--ignore-certificate-errors",
                   "--window-size=500,500",
                   "--silent",
                   "--blink-settings=imagesEnabled=false",
                   "--disable-notifications]"]
        chromePrefs.put("profile.default_content_settings.popups", 0)
        // chromePrefs.put("download.default_directory", targetPath)
        driver = context.createWebDriver(options, chromePrefs)
        return driver
    }

    def getNextPage(def url, def table1end, def table2end) {
        // From Table 1
        def btn1 = driver.findElement(By.xpath("//*[@id=\"table_1\"]"))
        for (int i = 2; i <= table1end; i++) { // 0 = 2 no page
            def next = driver.findElement(By.xpath("//*[@id=\"table_1_next\"]"))
            next.click()
            Thread.sleep(2000)
            def html = driver.getPageSource()
            getData(html)
        }

        // From Table 2
        def btn2 = driver.findElement(By.xpath("//*[@id=\"table_2\"]"))
        for (int i = 2; i <= table2end; i++) {
            def next = driver.findElement(By.xpath("//*[@id=\"table_2_next\"]"))
            next.click()
            Thread.sleep(2000)
            def html2 = driver.getPageSource()
            getData02(html2)
        }
    }

    def getData(def html) {
        def tableMatch = html =~ /(?ism)SECURITIES LEGAL ACTIONS.+?<tbody(.+?)<\\/tbody>/
        def table
        if (tableMatch.find()) {
            table = tableMatch.group(1).trim()
            def rowData
            def rowDataMatcher = table =~ /(?ism)<tr.+?<\/tr>/
            while (rowDataMatcher.find()) {
                rowData = rowDataMatcher.group(0)
                def name, entityUrl, Date
                def hearingDate
                def allDataMatcher = rowData =~ /(?ism)sorting_1">(.*?)<\\/td.+?">(.+?)<\\/td.+?href="(.+?)"/
                if (allDataMatcher.find()) {
                    name = nameSanitizeTableOne(allDataMatcher.group(2))
                    entityUrl = allDataMatcher.group(3)
                    entityUrl = entityUrl.toString().replaceAll(/View File/, "").trim()
                }

                def dateMatcher = rowData =~ /\d{1,}\/\d{1,}\/\d{2,}/
                if (dateMatcher.find()) {
                    hearingDate = dateMatcher.group(0)
                }
                def nameList = []
                if (name =~ /(?i)@@|;/) {
                    nameList = name.split(/@@|;/)
                } else {
                    nameList.add(name)
                }

                nameList.each {
                    if (it != null) {
                        def aliasList = []
                        def alias
                        if (it.toString().contains("d/b/a")) {
                            def aliasMatch = it =~ /(?ism)d\\/b\\/a(.+?)$/
                            if (aliasMatch.find()) {
                                def removePart = Pattern.quote(aliasMatch[0][0])
                                alias = aliasMatch[0][1]
                                alias = alias.toString().replaceAll(/d\/b\/a/, "")
                                it = it.replaceAll(/$removePart/, "").trim()
                                aliasList.add(alias)
                            }
                        }

                        if (it.toString().contains("a/k/a")) {
                            def aliasMatch = it =~ /(?ism)a\\/k\\/a(.+?)$/
                            if (aliasMatch.find()) {
                                def removePart = Pattern.quote(aliasMatch[0][0])
                                alias = aliasMatch[0][1]
                                alias = alias.toString().replaceAll(/a\/k\/a/, "")
                                it = it.replaceAll(/$removePart/, "").trim()
                                aliasList.add(alias)
                            }
                        }

                        if ((it.toString().contains(" AKA ")) || it.toString().contains(" aka ")) {
                            def aliasMatch = it =~ /(?ism)\saka\s(.+?)$/
                            if (aliasMatch.find()) {
                                def removePart = Pattern.quote(aliasMatch[0][0])
                                alias = aliasMatch[0][1]
                                alias = alias.toString().replaceAll(/ aka /, "")
                                it = it.replaceAll(/$removePart/, "").trim()
                                aliasList.add(alias)
                            }
                        }

                        if ((it.toString().contains(" DBA ")) || it.toString().contains(" dba ")) {
                            def aliasMatch = it =~ /(?ism)\sdba\s(.+?)$/
                            if (aliasMatch.find()) {
                                def removePart = Pattern.quote(aliasMatch[0][0])
                                alias = aliasMatch[0][1]
                                alias = alias.toString().replaceAll(/ dba /, "")
                                it = it.replaceAll(/$removePart/, "").trim()
                                aliasList.add(alias)
                            }
                        }
                        aliasList.each {
                            it = it.toString().replaceAll(/(?ism)^\s/, "").replaceAll(/(?s)\s+/, " ").trim()
                            if (it.contains("-alias-")) {
                                aliasList = it.split("-alias-").collect({ its -> return its })
                            }
                        }
                        it = it.toString().replaceAll(/,\s*$/, "").replaceAll(/^\s+/, "").trim()
                        it = it.toUpperCase()
                        createEntity(it, entityUrl, hearingDate, aliasList)
                    }
                }
            }
        }
    }

    def getData02(def html) {
        def tableMatch = html =~ /(?s)table_2_info">.+?<\/tbody>/

        def table
        while (tableMatch.find()) {
            table = tableMatch.group(0).trim()
            def rowData
            def rowDataMatcher = table =~ /(?ism)<tr class.+?<\\/tr>/
            while (rowDataMatcher.find()) {
                rowData = rowDataMatcher.group(0)
                def name, entityUrl, Date
                def hearingDate
                def allDataMatcher = rowData =~ /(?ism)sorting_1">(.*?)<\\/td.+?">(.+?)<\\/td.+?href="(.+?)"/
                if (allDataMatcher.find()) {
                    name = nameSanitizeTableTwo(allDataMatcher.group(2))
                    entityUrl = allDataMatcher.group(3)
                    entityUrl = entityUrl.toString().replaceAll(/View File/, "").trim()
                }

                def dateMatcher = rowData =~ /\d{1,}\/\d{1,}\/\d{2,}/
                if (dateMatcher.find()) {
                    hearingDate = dateMatcher.group(0)
                    hearingDate = hearingDate.toString().replaceAll(/0021/, "2021")
                    hearingDate = hearingDate.toString().replaceAll(/0009/, "2009")
                    hearingDate = hearingDate.toString().replaceAll(/0023/, "2023")
                }

                def nameList = []
                if (name =~ /(?i);/) {
                    nameList = name.split(/;/)
                } else {
                    nameList.add(name)
                }


                nameList.each {
                    if (it != null) {
                        def aliasList = []
                        def alias


                        if (it.toString().contains("a/b/n")) {
                            def aliasMatch = it =~ /(?ism)a\\/b\\/n (.+?)$/
                            if (aliasMatch.find()) {
                                def removePart = Pattern.quote(aliasMatch[0][0])
                                alias = aliasMatch[0][1]
                                alias = alias.toString().replaceAll(/a\/b\/n/, "")
                                it = it.replaceAll(/$removePart/, "").trim()
                                aliasList.add(alias)
                            }
                        }

                        if (it.toString().contains("dba ")) {
                            def aliasMatch = it =~ /(?ism)dba (.+?)$/
                            if (aliasMatch.find()) {
                                def removePart = Pattern.quote(aliasMatch[0][0])
                                alias = aliasMatch[0][1]
                                alias = alias.toString().replaceAll(/dba/, "")
                                it = it.replaceAll(/$removePart/, "").trim()
                                aliasList.add(alias)
                            }
                        }

                        if ((it.toString().contains(" AKA ")) || it.toString().contains(" aka ")) {
                            def aliasMatch = it =~ /(?ism)\saka\s(.+?)$/
                            if (aliasMatch.find()) {
                                def removePart = Pattern.quote(aliasMatch[0][0])
                                alias = aliasMatch[0][1]
                                alias = alias.toString().replaceAll(/ aka /, "")
                                it = it.replaceAll(/$removePart/, "").trim()
                                aliasList.add(alias)
                            }
                        }

                        aliasList.each {
                            it = it.toString().replaceAll(/(?ism)^\s/, "").replaceAll(/(?i),\sJr\.$/,"").replaceAll(/(?s)\s+/, " ").trim()
                            if (it.contains("##")) {
                                aliasList = it.split("##").collect({ its -> return its })

                            }
                        }

                        it = it.toString().replaceAll(/^\s{1,}|,\s*$/, "").replaceAll(/(?i),\sJr\.$/,"").trim()
                        it = it.toUpperCase()
                        createEntity(it, entityUrl, hearingDate, aliasList)
                    }
                }
            }

        }
    }

    def nameSanitizeTableOne(def name) {
        name = name.toString().replaceAll(/(?ism)(?:In the matter of the |The )*Proposed Agency Action Against |State of Montana vs\.|State of Montana v\. /, "")
        name = name.toString().replaceAll(/&amp;/, "&")
        name = name.toString().replaceAll(/125313, and James Jarratt/, "125313 James Jarratt")
        name = name.toString().replaceAll(/(?ism), A Deleware Corporation|, a Delaware Corporation/, "")
        name = name.toString().replaceAll(/(?ism)com, Metals.com, Chase Metals, LLC, Chase Metals, INC., Barrick Capital/, "com-alias-Metals.com-alias-Chase Metals, LLC-alias-Chase Metals, INC.-alias-Barrick Capita")
        name = name.toString().replaceAll(/(?ism)Proposed Agency Action  Against/, "")
        name = name.toString().replaceAll(/PAUL MACDOWELL, DARREN OLAY AN, REID TAN/, "PAUL MACDOWELL @@DARREN OLAY @@REID TAN")
        name = name.toString().replaceAll(/(?ism)AKA, NUI SOCIAL, LLC, SYMA TRI, LLC, and MINTAGE MINING LLC/, "aka NUI SOCIAL, LLC-alias-SYMA TRI, LLC-alias-MINTAGE MINING LLC")
        name = name.toString().replaceAll(/(?ism), et.Al./, "")
        name = name.toString().replaceAll(/(?ism)LLC; Joshua Patrick Stoll; and Julie/, "LLC @@Joshua Patrick Stoll @@Julie")
        name = name.toString().replaceAll(/(?ism)Inc., Metals|Inc, Metals/, "Inc.@@Metals")
        name = name.toString().replaceAll(/(?ism)Lindberg and First/, "Lindberg  @@First")
        name = name.toString().replaceAll(/(?ism)v State of Montana|State of Montana vs /, "")
        name = name.toString().replaceAll(/, a Montana licensed broker-dealer; Anthony D. Branca; Joseph L. Derrico; Ryan Murnane/, "@@Anthony D. Branca @@Joseph L. Derrico @@Ryan Murnane")
        name = name.toString().replaceAll(/(?ism), Hunt Insurance Group, Inc., and Unicare Insurance, Inc./, "@@Hunt Insurance Group, Inc.@@Unicare Insurance, Inc.")
        name = name.toString().replaceAll(/, a Montana license broker-dealer; /, "@@")
        name = name.toString().replaceAll(/Gennity; Joseph Connolly; Francine Lanaia; Barry/, "Gennity @@Joseph Connolly@@Francine Lanaia@@Barry")
        name = name.toString().replaceAll(/Eisenberg; Timothy Stack; Rocco Guidicipietro; and Ryan Murnane/, "Eisenberg@@Timothy Stack@@Rocco Guidicipietro@@Ryan Murnane")
        name = name.toString().replaceAll(/Dowell, Darren Olayan, Reid Tanaka, NUI Social, LLC, Symatri, LLC, and Mintage Mining LLC/, "Dowell@@Darren Olayan@@Reid Tanaka@@NUI Social, LLC@@Symatri, LLC@@Mintage Mining LLC")
        name = name.toString().replaceAll(/(?ism), individually.+?employee(?:; et al.)*/, "")
        name = name.toString().replaceAll(/Sullivan; and Frank/, "Sullivan@@Frank ")
        name = name.toString().replaceAll(/, an individual, and Elite/, "@@Elite")
        name = name.toString().replaceAll(/, a Delaware Limited Liability Company|, a Nevada Limited Liability Company/, "")
        name = name.toString().replaceAll(/, L.P., a Montana licensed broker-dealer; William/, ", L.P.@@William")
        name = name.toString().replaceAll(/, an individual, and Universal/, "@@Universal")
        name = name.toString().replaceAll(/, a Montana licensed broker-dealer: William/, "@@William")
        name = name.toString().replaceAll(/Investment Company Institute v. Monica Lindeen, State Auditor/, "Monica Lindeen")
        name = name.toString().replaceAll(/In the Matter of: /, "")
        name = name.toString().replaceAll(/Koostra, First/, "Koostra@@First")
        name = name.toString().replaceAll(/LLC, Fidelity First/, "LLC@@Fidelity First")
        name = name.toString().replaceAll(/Company and BLC Financial/, "Company@@BLC Financial")
        name = name.toString().replaceAll(/Inc. and Richard F/, "Inc.@@Richard F")
        name = name.toString().replaceAll(/(?ism)Moschetta and Strasbourger/, "Moschetta@@Strasbourger")
        name = name.toString().replaceAll(/, LLC,\s(?:and\s)*/, ", LLC@@")
        name = name.toString().replaceAll(/Co. and BLC/, "Co.@@BLC")
        name = name.toString().replaceAll(/(?ism)nard, DC Wealth Management, Inc., DC Asso/, "nard@@DC Wealth Management, Inc.@@DC Asso")
        name = name.toString().replaceAll(/(?ism)Matthew.+?\w+\sv\.\s/, "")
        name = name.toString().replaceAll(/Converse and DC/, "Converse@@DC")
        name = name.toString().replaceAll(/Montana, and /, "Montana@@")
        name = name.toString().replaceAll(/(?ism) CRD #\d{3,}|a Montana licensed broker-dealer; /, "")
        name = name.toString().replaceAll(/(?ism)sen; Poker Junkies Production, LLC; and GAWK/, "sen@@Poker Junkies Production, LLC@@GAWK")
        name = name.toString().replaceAll(/(?ism), individually.+?vices LLC|Monica J. Lindeen v. /, "")
        name = name.toString().replaceAll(/(?ism)LLC, and Duane/, "LLC@@Duane")
        name = name.toString().replaceAll(/(?ism)Moore, Glacier Gala, Haystack/, "Moore@@Glacier Gala@@Haystack")
        name = name.toString().replaceAll(/LLC and Martin/, "LLC@@Martin")
        name = name.toString().replaceAll(/(?ism)Monica.+? v\.\s|(?ism), individually.+?ciates, L.P./, "")
        name = name.toString().replaceAll(/L.P. and William/, "L.P.@@William")
        name = name.toString().replaceAll(/Inc.; Telexfree/, "Inc.@@Telexfree")
        name = name.toString().replaceAll(/(?ism)Merrill; and Carlos/, "Merrill@@Carlos")
        name = name.toString().replaceAll(/(?ism)Willis, Albert Michael Willis, Matthew Cody McClintock, Odell McClintock, Michael Dean McClintock, Michael Odell, Michael/, "Willis@@Albert Michael Willis@@Matthew Cody McClintock@@Odell McClintock@@Michael Dean McClintock@@Michael Odell@@Michael")
        name = name.toString().replaceAll(/(?ism)Willis, Albert Michael Willis, Mathew Cody McClintock, Odell McClintock, Michael Dean McClintock, Michael Odell, Michael/, "Willis@@Albert Michael Willis@@Mathew Cody McClintock@@Odell McClintock@@Michael Dean McClintock@@Michael Odell@@Michael")
        name = name.toString().replaceAll(/(?ism),  individually.+?Investors, LLC/, "")
        name = name.toString().replaceAll(/Inc., and Edward/, "Inc.@@Edward")
        name = name.toString().replaceAll(/(?ism)Matthew McClintock aka.+?Willis,.+?Films/, "Matthew McClintock aka Michael Willis@@Albert Michael Willis@@Matthew Cody McClintock@@Odell McClintock@@Michael Dean McClintock@@Michael Odell@@Michael Albert Willis dba Bar M Films")
        name = name.toString().replaceAll(/Inc. and Home/, "Inc.@@Home")
        name = name.toString().replaceAll(/(?ism)Capital, William.+?eth Williams/, "Capital@@William Horbatuk@@Halil Kozi@@Robert Delaplain@@Kevin Chen@@Kenneth Williams")
        name = name.toString().replaceAll(/(?ism)Inc., And Raymond/, "Inc.@@Raymond")
        name = name.toString().replaceAll(/(?ism)Inc, and Morgan/, "Inc@@Morgan")
        name = name.toString().replaceAll(/(?ism) in her capacity.+?representative/, "")
        name = name.toString().replaceAll(/(?ism)Wagner and J.F/, "Wagner@@J.F")
        name = name.toString().replaceAll(/(?ism)Willis, Alber Michael Willis, Mathew Cody McClintock, Odell McClintock, Michael Dean McClintock, Michael Odell, Michael Albert Willis,/, "Willis@@Alber Michael Willis@@Mathew Cody McClintock@@Odell McClintock@@Michael Dean McClintock@@Michael Odell@@Michael Albert Willis")
        name = name.toString().replaceAll(/(?ism)Montana v. /, "")
        name = name.toString().replaceAll(/(?ism)Jaffray, Thomas J/, "Jaffray@@Thomas J")
        name = name.toString().replaceAll(/(?ism)Financial \(Robert J. Congdon\)/, "Financial@@Robert J. Congdon")
        name = name.toString().replaceAll(/(?ism)Nelson \(RBC Capital Markets, LLC\)/, "Nelson@@RBC Capital Markets, LLC")
        name = name.toString().replaceAll(/(?ism)The State v. |State of Montana v /, "")
        name = name.toString().replaceAll(/(?ism)James "Jeb" Bryant/, "James Bryant aka Jeb")
        name = name.toString().replaceAll(/Seibert, aka/, "Seibert aka")
        name = name.toString().replaceAll(/(?ism)Barry Eisenberg; Timothy Stack; and Rocco/, "Barry Eisenberg@@Timothy Stack@@Rocco")
        name = name.toString().replaceAll(/Eisenberg; and Robert/, "Eisenberg@@Robert")
        name = name.toString().replaceAll(/(?ism)Inc.; and Paul L/, "Inc.@@Paul L")
        name = name.toString().replaceAll(/aka ichael/, "aka Michael")
        name = name.toString().replaceAll(/(?ism), a.k.a. Blackwell/, " aka Blackwell")
        name = name.toString().replaceAll(/(?ism)f\/k\/a Common Cents/, "aka Common Cents")
        name = name.toString().replaceAll(/(?ism), dba|, aka|A\/K\/A|, d\/b\/a\/|d\/b\/a\//, " dba")
        name = name.toString().replaceAll(/Brandt \(Rick\)/, "Brandt aka Rick")
        name = name.toString().replaceAll(/: and "The Shark of Wall Street"/, "@@The Shark of Wall Street")
        name = name.toString().replaceAll(/MyConstant, Constant, and Constant/, "MyConstant-alias-Constant-alias-Constant")
        name = name.toString().replaceAll(/, a foreign entity|(?i)Dividends & More(,| and )|,*\s*CRD No. \d{4,}|Richard A. Hill v. /, "")
        name = name.toString().replaceAll(/Lanaia and Ryan/, "Lanaia@@Ryan")
        name = name.toString().replaceAll(/, AMY PRICE, P/, "@@AMY PRICE@@P")
        name = name.toString().replaceAll(/Brandt, Brandt/, "Brandt@@Brandt")
        name = name.toString().replaceAll(/@@a Delaware Limited Liability Corporation/, "")
        name = name.toString().replaceAll(/(?ism)Cornerstone Financial \(Keith Kovick\)/, "Keith Kovick")
        name = name.toString().replaceAll(/ and BLC Inc./, "@@BLC Inc.")
        name = name.toString().replaceAll(/, Amy Price, P /, ";Amy Price;P ")
        name = name.toString().replaceAll(/, Richard Anthony Russo, Ferdinand Russo and /, ";Richard Anthony Russo;Ferdinand Russo;")
        name = name.toString().replaceAll(/(?i)\(Rick\) Brandt/, "Brandt aka Rick")
        name = name.toString().replaceAll(/and James/, "James")
        name = name.toString().replaceAll(/and Rimrock/, ";Rimrock")
        name = name.toString().replaceAll(/sage and /, "sage Okhotnikov;")
        name = name.toString().replaceAll(/Genworth Life/, "Genworth Life Insurance Company")
        name = name.toString().replaceAll(/Fox, Kath/, "Fox;Kath")
        name = name.toString().replaceAll(/The  Wi/, "Wi")
        name = name.toString().replaceAll(/(?i)MONAVIE/, "MONAVIE, LLC")
        name = name.toString().replaceAll(/(?ism),\s*$/, "").trim()
        return name
    }

    def nameSanitizeTableTwo(def name) {
        name = name.toString().replaceAll(/(?ism) \(License No. 12875959\), and /, ";")
        name = name.toString().replaceAll(/(?ism)^Proposed Agency Action Against |(?ism)\(License No\.\s\d{8,}\)(?:, Respondents.)*/, "")
        name = name.toString().replaceAll(/(?ism)Victory Insurance Company.+?(?:the Commissioner|Victory's Customers)/, "Victory Insurance Company")
        name = name.toString().replaceAll(/(?ism)\sa Special.+?ce Company/, "")
        name = name.toString().replaceAll(/corporation, and American/, "corporation;American")
        name = name.toString().replaceAll(/(?ism)Aka Jenna/, "dba Jenna")
        name = name.toString().replaceAll(/(?ism)LLC and Harvest Management Sub LLC, Holiday/, "LLC;Harvest Management Sub LLC;Holiday")
        name = name.toString().replaceAll(/(?ism), Montana.+?License \d{8,}/, "")
        name = name.toString().replaceAll(/Bonds, MT Bail Bonds, ASAP Bail Bonds, Alec/, "Bonds##MT Bail Bonds##ASAP Bail Bonds##Alec")
        name = name.toString().replaceAll(/^State of Montana vs\. |(?ism)\(Lic\..+?\d{8,}\)|, License.+?\d{3,}$/, "")
        name = name.toString().replaceAll(/(?ism)\(License No\.\s*\d{4,}\)|, LIC#3000437099|State of Montana v\.\s*|,*\s*Insurance Producer License\s#\d{5,}/, "")
        name = name.toString().replaceAll(/Montana Certificate of Authority # 6081/, "")
        name = name.toString().replaceAll(/Company, and New/, "Company;New")
        name = name.toString().replaceAll(/Inc. and ICDC/, "Inc.;ICDC")
        name = name.toString().replaceAll(/Jr.\s*$|The State of Montana vs. | \(Unapproved Forms\)/, "")
        name = name.toString().replaceAll(/Pryor and Tommy/, "Pryor;Tommy")
        name = name.toString().replaceAll(/Company and Colorado/, "Company;Colorado")
        name = name.toString().replaceAll(/, with and into /, ";")
        name = name.toString().replaceAll(/Company, and American/, "Company;American")
        name = name.toString().replaceAll(/(?ism)Graber, \(dba\) Tennessee/, "Graber dba Tennessee")
        name = name.toString().replaceAll(/(?ism)Inc., Crown Captive Insurance, Inc., Dermsea Insurance, Inc., Dunn &amp; Dunner Insurance, Inc., Edge Insurance, Inc., et al./, "Inc.;Crown Captive Insurance, Inc.;Dermsea Insurance, Inc.;Dunn & Dunner Insurance, Inc.;Edge Insurance, Inc.")
        name = name.toString().replaceAll(/&amp;/, "&")
        name = name.toString().replaceAll(/(?ism)a\.k\.a\./, "aka")
        name = name.toString().replaceAll(/(?ism), an Individual \(dba\)/, " dba")
        name = name.toString().replaceAll(/, a self-funded MEWA|RRG, a North Carolina LLC|RRG, a Montana LLC|;\sa Delaware.+?Company|, et al.|, a Belize.+?Liability Company|The Acquisition.+?Insurance Company by /, "")
        name = name.toString().replaceAll(/; and| by and into/, ";")
        name = name.toString().replaceAll(/(?ism)Corporation and US Alliance Life/, "Corporation;US Alliance Life")
        name = name.toString().replaceAll(/^The Proposed Merger of |Acquisition of Great |; et al\.|; et al|The proposed merger of: |, et. al./, "")
        name = name.toString().replaceAll(/Company and Granite/, "Company;Granite")
        name = name.toString().replaceAll(/Insurance Co. by US Alliance Life and Security Co. and US/, "Insurance Co.;US Alliance Life and Security Co.;US")
        name = name.toString().replaceAll(/(?ism)LLC and LONNIE/, "LLC;LONNIE")
        name = name.toString().replaceAll(/(?ism)Company, HCC Medi/, "Company;HCC Medi")
        name = name.toString().replaceAll(/(?ism),* by American.+?ance Company|In the Re Acquisition of: |The Proposed Merger of: |(?ism), a St.+?Company|(?:\sProtected Cell \d{4}-\d{3})*, a Montana .+?Company(?:. Applicants.)*/, "")
        name = name.toString().replaceAll(/In Re /, "")
        name = name.toString().replaceAll(/(?ism)Inc., Coolidge Insurance Company, Inc., and Harding Insurance Company, Inc. with and into Van/, "Inc.;Coolidge Insurance Company, Inc.;Harding Insurance Company, Inc.;Van")
        name = name.toString().replaceAll(/INC. and JON/, "INC.;JON")
        name = name.toString().replaceAll(/ et al.|; \.|The Insurance Producer License of /, "")
        name = name.toString().replaceAll(/ife and Annuity/, "ife;Annuity")
        name = name.toString().replaceAll(/(?ism)Company \(Greg Zahn\)/, "Company aka Greg Zahn")
        name = name.toString().replaceAll(/(?ism)Company \(Assurant, Inc\)/, "Company aka Assurant, Inc")
        name = name.toString().replaceAll(/(?ism), an individual, and /, ";")
        name = name.toString().replaceAll(/(?ism), a Missour.+?company/, "")
        name = name.toString().replaceAll(/Diamond and Associates/, "Diamond;Associates")
        name = name.toString().replaceAll(/Inc., William Rupnow, Theresa/, "Inc.;William Rupnow;Theresa")
        name = name.toString().replaceAll(/(?ism), d\/b\/a|, a\/k\/a\//, " aka")
        name = name.toString().replaceAll(/(?ism)Inc. and Health Ca/, "Inc.;Health Ca")
        name = name.toString().replaceAll(/(?ism)Richard "Rick" Schaeffer/, "Richard Schaeffer aka Rick")
        name = name.toString().replaceAll(/(?ism) f\.k\.a |, DBA | a\.k\.a /, " aka ")
        name = name.toString().replaceAll(/(?ism)In the United.+?Fossen, v\. |, UGP, NHCMG, CAUSA/, "")
        name = name.toString().replaceAll(/(?ism)Benefits, Fairmont Premier Insurance Co, United/, "Benefits;Fairmont Premier Insurance Co;United")
        name = name.toString().replaceAll(/Inc., Adova/, "Inc.;Adova")
        name = name.toString().replaceAll(/Mierzwa and Mark Krawczyk/, "Mierzwa##Mark Krawczyk")
        name = name.toString().replaceAll(/State v\. /, "")
        name = name.toString().replaceAll(/L\. and Lawrence/, "L.;Lawrence")
        name = name.toString().replaceAll(/ by PacificSource Health Plans|Bulk of Reinsurance of | by PacificSource Health Plans/, "")
        name = name.toString().replaceAll(/Wilson, Surety/, "Wilson;Surety")
        name = name.toString().replaceAll(/Contractors \(IEC\)/, "Contractors aka IEC")
        name = name.toString().replaceAll(/uote \(Udell-Sachs\)/, "uote aka Udell-Sachs")
        name = name.toString().replaceAll(/Company and First/, "Company;First")
        name = name.toString().replaceAll(/Incorporated, and Charley/, "Incorporated;Charley")
        name = name.toString().replaceAll(/Choice, Stephen/, "Choice;Stephen")
        name = name.toString().replaceAll(/(?ism)Sr. \(License.+?927720/, "")
        name = name.toString().replaceAll(/(?ism)License and the Insurance/, "License;The Insurance")
        name = name.toString().replaceAll(/Klima and Standard/, "Klima;Standard")
        name = name.toString().replaceAll(/Health, Med/, "Health;Med")
        name = name.toString().replaceAll(/ et al$/, "")
        name = name.toString().replaceAll(/Co., Hartford/, "Co.;Hartford")
        name = name.toString().replaceAll(/(?ism)Company, AON Services Group, AON Association Services Division, Hungington T. Block Association Services, and AON/, "Company;AON Services Group;AON Association Services Division;Hungington T. Block Association Services;AON")
        name = name.toString().replaceAll(/, a Mutual Legal Reserve Company/, "")
        name = name.toString().replaceAll(/Montana, a Division of/, "Inc;")
        name = name.toString().replaceAll(/(?ism)Company and Hartford/, "Company;Hartford")
        name = name.toString().replaceAll(/Inc, A Risk|Inc\. a Risk/, "Inc;A Risk")
        name = name.toString().replaceAll(/LLC, and Steven/, "LLC;Steven")
        name = name.toString().replaceAll(/Company and Allegiance/, "Company;Allegiance")
        name = name.toString().replaceAll(/(?ism)LLP and Brady C/, "LLP;Brady C")
        name = name.toString().replaceAll(/Company and Tupelo/, "Company;Tupelo")
        name = name.toString().replaceAll(/(?ism)Inc., and Miraj/, "Inc.;Miraj")
        name = name.toString().replaceAll(/Company and Koman/, "Company;Koman")
        name = name.toString().replaceAll(/Company and Carthage/, "Company;Carthage")
        name = name.toString().replaceAll(/Company and Canton/, "Company;Canton")
        name = name.toString().replaceAll(/Kleber and Rosemarie/, "Kleber;Rosemarie")
        name = name.toString().replaceAll(/(?ism), Montana.+?Corporation/, "")
        name = name.toString().replaceAll(/Illinois, Safeco Insurance Company of Oregon, General Insurance Company of America, Liberty Mutual Fire Insurance Company, The First Liberty Insurance Corporation, and Liberty/, "Illinois;Safeco Insurance Company of Oregon;General Insurance Company of America;Liberty Mutual Fire Insurance Company;The First Liberty Insurance Corporation;Liberty")
        name = name.toString().replaceAll(/(?ism)\(CoCode.+?company/, "")
        name = name.toString().replaceAll(/(?ism), a\/k\/a|, aka|, formerly|(?i)A\/b\/n/, " aka")
        name = name.toString().replaceAll(/Inc.,/, "Inc.")
        name = name.toString().replaceAll(/Corporation, and American/, "Corporation;American")
        name = name.toString().replaceAll(/Aipperspach, Bradley Blane/, "Bradley Blane Aipperspach")
        name = name.toString().replaceAll(/pensieri, ASAP/, "pensieri;ASAP")
        name = name.toString().replaceAll(/ License No. 3001012322/, "")
        name = name.toString().replaceAll(/LLC, and CareSource/, "LLC;CareSource")
        name = name.toString().replaceAll(/ \(aka Dawn Bergan\)/, " aka Dawn Bergan")
        name = name.toString().replaceAll(/Coulombe, /, "Coulombe")
        name = name.toString().replaceAll(/ Insurance Co.+?Montana Corporation|, Montana Producer License, 3000344035|Insurance Producer License of /, "")
        name = name.toString().replaceAll(/Merger and Redomestication/, "Merger;Redomestication")
        name = name.toString().replaceAll(/Hanser and Rhonda/, "Hanser;Rhonda")
        name = name.toString().replaceAll(/INC Business Entities/, "INC")
        name = name.toString().replaceAll(/, Respondent|Proposed Agency Against |Proposed Merger of /, "")
        name = name.toString().replaceAll(/Watts, Ronald/, "Ronald Watts")
        name = name.toString().replaceAll(/Dubin and Bridgitte/, "Dubin;Bridgitte")
        name = name.toString().replaceAll(/Ellison, Lionel/, "Lionel Ellison")
        name = name.toString().replaceAll(/The James/, "James")
        name = name.toString().replaceAll(/The Karla Ann Fisher/, "Karla Ann Fisher")
        name = name.toString().replaceAll(/^and\s|, No. 6094|a Montana corporation,|(?i)A MONTANA PROTECTED CELL CAPTIVE INSURANCE CORPORATION|A MONTANA PROTECTED CELL CAPTIVE INSURANCE CORPORATION|Plaintiff v. |, RRG Certificate of Authority #4991|a Montana protected cell corporation|a Montana corporation|\d{4}-\d,\sa Utah corporation|Protected Cell\s+\d{4}-\d{3}/, "")
        name = name.toString().replaceAll(/( and )(Forshee|Accuserve|Rosebud|Pacific Northwest)/, ';$2')
        name = name.toString().replaceAll(/(?i)The Proposed Agency Action Against |, SBS Company Number 29178170/, "")
        name = name.toString().replaceAll(/,  Catherine Einhause, Douglas Wefer, D/, ";Catherine Einhause;Douglas Wefer;D")
        name = name.toString().replaceAll(/and Lonnie/, ";Lonnie")
        name = name.toString().replaceAll(/(?i)ACQUISITION OF EXPRESS SCRIPTS HOLDING COMPANY BY|AMENDED VOLUNTARY DISSOLUTION OF /, "")
        name = name.toString().replaceAll(/Company of Texas/, "Company")
        name = name.toString().replaceAll(/ LLC, Geoffrey Johnsey, Ronald A. Sweatt, Terry Woodbury, Ryan/, " LLC;Geoffrey Johnsey;Ronald A. Sweatt;Terry Woodbury;Ryan")
        name = name.toString().replaceAll(/Inc. and Alliance/, "Inc.;Alliance")
        name = name.toString().replaceAll(/(?i)(and )(Safeco|Express Scripts|Miraj|Jon Joseph|Principal Life)/, ';$2')
        name = name.toString().replaceAll(/(?i) a Texas Company registered to do business in Montana, and |AND ITS DIVISION |WITH AND INTO /, ";")
        name = name.toString().replaceAll(/, individually|(?i)CERTIFICATE OF AUTHORITY NO. 6597|Voluntary Dissolution of /, "")
        name = name.toString().replaceAll(/Inc. Amwest Life Assurance Corp., Wilfrid Rumball, and /, "Inc.;Amwest Life Assurance Corp.;Wilfrid Rumball;")
        name = name.toString().replaceAll(/(?i)The Report of Market Conduct Examination of |THE PROPOSED MERGERS OF|The Bulk Reinsurance of: | vs. State of Montana|Bulk Reinsurance of | by PacificSource/, "")
        name = name.toString().replaceAll(/Montana Benefits/, "Montana Benefits and Health Connection Inc.")
        name = name.toString().replaceAll(/, by Flathead/, ";Flathead")
        name = name.toString().replaceAll(/(?i)CHICAGO TITLE COMPANY OF MONTANA/, "CHICAGO TITLE INSURANCE CO.")
        name = name.toString().replaceAll(/(?i), a Montana LLC|a Belize Corporation|Proposed Agency Action Action |REPORT OF THE EXAMINATION OF |THE ACQUISITION OF |THE ALLIANCE OF |THE AQUISITION OF |The Insurance Agency License of |The License of |The Liquidation of |The Proposed Disciplinary Treatment of /, "")
        name = name.toString().replaceAll(/(?i)LAWRENCE L./, "LAWRENCE L. SKAWINSKI")
        name = name.toString().replaceAll(/(?s)Jai Alai.+?Carolina/, "Mountain Captive Insurance, Inc.")
        name = name.toString().replaceAll(/(?i)ELL AND SHE/, "ELL;SHE")
        name = name.toString().replaceAll(/(?i)ce, Thomas Carroll, Catherine Einhaus Douglas Wefer D.J. Colby Co. Inc. Respondent/, "ce;Thomas Carroll;Catherine Einhaus;Douglas Wefer;D.J. Colby Co. Inc.")
        name = name.toString().replaceAll(/(?i)Johnson aka Gianna MARIA STUFF/, "Johnson ##Gianna MARIA STUFF")
        return name
    }
    def detectEntity(def name) {
        def type
        if (name =~ /^\S+$/) {
            type = "O"
        } else {
            type = entityType.detectEntityType(name)
            if (type.equals("P")) {
                if (name =~ /(?i)(?:Pharmacy|Medicine|Imaging|P\.C\.|MHA Workers|HELPING WOMEN|AGRICAP ASSURANCE|MED O|BLUE CROSS BLUE|Cascade Co|ACCORDIA LI|INFINITY ROOFING)/) {
                    type = "O"
                }
            } else if (name =~ /(?i)(?:THOMAS)/) {
                type = "P"
            }
        }
        return type
    }

    def createEntity(def name, def entityUrl, def hearingDate, def aliasList) {
        def entity = null
        if (!name.toString().isEmpty()) {
            def entityType = detectEntity(name)
            entity = context.findEntity("name": name, "type": entityType)
            if (!entity) {
                entity = context.getSession().newEntity()
                entity.setName(name)
                entity.setType(entityType)
            }


            if (entityUrl) {
                entity.addUrl(entityUrl)
            }
            if (aliasList) {
                aliasList.each {
                    it = it.toString().replaceAll(/(?ism)^\s/, "").replaceAll(/(?s)\s+/, " ").trim()
                    entity.addAlias(it)
                }
            }
            ScrapeEvent event = new ScrapeEvent()
            event.setDescription(eventDes)
            if (hearingDate) {
                def date = context.parseDate(new StringSource(hearingDate), ["MM/dd/yyyy", "yyyy-MM-dd"] as String[])
                event.setDate(date)
            }
            entity.addEvent(event)
            ScrapeAddress address = new ScrapeAddress()
            address.setProvince("Montana")
            address.setCountry("UNITED STATES")
            entity.addAddress(address)

        }
    }
}