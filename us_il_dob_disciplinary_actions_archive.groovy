package current_scripts

import com.rdc.importer.scrapian.ScrapianContext
import com.rdc.importer.scrapian.model.StringSource
import com.rdc.importer.scrapian.util.ModuleLoader
import com.rdc.scrape.ScrapeAddress
import com.rdc.scrape.ScrapeEvent


context.setup([connectionTimeout: 20000, socketTimeout: 20000, retryCount: 1, multithread: true, userAgent: "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_13_6) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/80.0.3987.163 Safari/537.36"])
context.session.encoding = "UTF-8"
context.session.escape = true

US_IL_DOB_DISCIPLINARY_ACTIONS_ARCHIVE script = new US_IL_DOB_DISCIPLINARY_ACTIONS_ARCHIVE(context)
script.initParsing()

class US_IL_DOB_DISCIPLINARY_ACTIONS_ARCHIVE {
    final addressParser
    final entityType
    final def moduleFactory
    final def moduleLoaderVersion
    ScrapianContext context = new ScrapianContext()

    static def root = "https://idfpr.illinois.gov"
    static def url = "https://idfpr.illinois.gov/banks/agency/discipline.html"
    def description = "This entity appears on the Illinois Division of Banking’s list of Disciplinary Actions."

    US_IL_DOB_DISCIPLINARY_ACTIONS_ARCHIVE(context) {
        this.context = context
        moduleLoaderVersion = context.scriptParams.moduleLoaderVersion
        moduleFactory = ModuleLoader.getFactory(moduleLoaderVersion)
        entityType = moduleFactory.getEntityTypeDetection(context)
        addressParser = moduleFactory.getGenericAddressParser(context)
    }

    def initParsing() {
        def html = invoke(url)
        getUrlFromHtml(html)
    }

    def getUrlFromHtml(def html) {
        def subUrl
        def subUrlMatcher = html =~ /(?ism)table-responsive"><a href="(\/.+?)"/
        while (subUrlMatcher.find()) {
            def html2, mortgare_pdf, mortgage_Url, loanOrigin_Url
            def banks_Url
            subUrl = root + subUrlMatcher.group(1)

            if (subUrl =~ /(?s)cbt\/enforcement.html/) {
                html2 = context.invoke([url: subUrl, tidy: false, cache: false])
                forBanks_Trust_Companies(html2)
            }
            if (subUrl =~ /(?s)\/enforcementmain.html/) {
                html2 = context.invoke([url: subUrl, tidy: false, cache: false])
                forMortgage_Banking(html2)
            }
            if (subUrl =~ /(?s)pawnbrok\/pawnenforcement.html/) {
                html2 = context.invoke([url: subUrl, tidy: false, cache: false])
                Pawnbrokers(html2)
            }
            if (subUrl =~ /(?s)lodisciplines.html/) {
                html2 = context.invoke([url: subUrl, tidy: false, cache: false])
                IoDiscipline(html2)
            }
        }
    }

    def forBanks_Trust_Companies(def html) {
        def multipleUrl
        int year
        def urlAndYearMatcher = html =~ /(?im)<a href="(.+?)" target.+?">(\d{4})/
        while (urlAndYearMatcher.find()) {
            multipleUrl = root + urlAndYearMatcher.group(1)
            year = Integer.parseInt(urlAndYearMatcher.group(2))
            def yearData
            if (year != 2023) {
                yearData = context.invoke([url: multipleUrl, tidy: false, cache: false])
                getDataFromBanksHtml(yearData)
            }
        }
    }

    def forMortgage_Banking(def html) {
        def multipleUrl
        int year
        def urlAndYearMatcher = html =~ /(?m)<a href="(\\/ba.+?)"\s*target.+?Actions\s(\d{4})/
        while (urlAndYearMatcher.find()) {
            multipleUrl = root + urlAndYearMatcher.group(1)
            year = Integer.parseInt(urlAndYearMatcher.group(2))
            def yearData
            if (year != 2023) {
                yearData = context.invoke([url: multipleUrl, tidy: false, cache: false])
                getDataFrom_mortagehtml(yearData)
            }
        }
    }

    def Pawnbrokers(def html) {
        def multipleUrl
        int year
        def urlAndYearMatcher = html =~ /(?m)<a href="(.+?)".+?">(\d{4})/
        while (urlAndYearMatcher.find()) {
            multipleUrl = root + urlAndYearMatcher.group(1)
            year = Integer.parseInt(urlAndYearMatcher.group(2))
            def yearData
            if (year != 2023) {
                yearData = context.invoke([url: multipleUrl, tidy: false, cache: false])
                getDataFromPawnBrokerHtml(yearData)
            }
        }
    }

    def IoDiscipline(def html) {
        def tableMatcher = html =~ /(?sm)<strong>\d{4}<\/strong>.+?<\/tr><\/tbody>/
        def tableData
        if (tableMatcher.find()) {
            tableData = tableMatcher.group(0)
            def rowMatcher = tableData =~ /(?s)<tr>.+?<\/tr>/
            while (rowMatcher.find()) {
                def row = rowMatcher.group(0)
                def nameUrlMatcher = row =~ /(?m)<a href="(.+?)".*?>\d{4}.+?\d{1,}(.+?)\d{2,}.+?<br \\/>/
                def name, entityUrl, date, address
                def subUrlList = []
                if (nameUrlMatcher.find()) {
                    subUrlList.add(root + nameUrlMatcher.group(1))
                    name = nameUrlMatcher.group(2)
                    name = name.toString().replaceAll(/(?ism)�|-/, "")
                    name = name.toString().replaceAll(/(.+?)(Michael R|Strauss|Zaba|Steven)/, '$2')
                    name = name.toString().replaceAll(/(.+?)(Louis)/, ' $2')
                    name = name.toString().replaceAll(/(Jeffrey)(.+)/, '$1')
                    name = name.toString().replaceAll(/(ley C)(.+)/, '$1')
                    name = name.toString().replaceAll(/Zaba, Wesley C/, "STONEWALL MORTGAGE CORPORATION dba IRONBANK MORTGAGE c/oWesley C Zaba")
                    name = name.toString().replaceAll(/(.+?)(,\s)(.+)/, '$3 $1').trim()


                    if (name != null) {
                        def multipleName = separateMultipleName(name)
                        multipleName.each {
                            if (it) {
                                def aliasList = []
                                def nameAliasList = splitNameAlias(it)
                                it = nameAliasList[0]
                                if (nameAliasList.size() > 1) {
                                    aliasList.add(nameAliasList[1])
                                }

                                entityCreation(it, subUrlList, null, date, aliasList, address)
                            }
                        }
                    }
                }
            }
        }
    }

    def sanitizePdfAddress(def address) {
        address = address.toString().replaceAll(/(?ism)License for 180|days|number|mortgage loan|License(?:e)*|Suspension for|Fine/, "")
        address = address.toString().replaceAll(/(?s)\s+/, " ").trim()
        address = address.toString().replaceAll(/(?ism)page\s\d.+?\/\d{1,2}\/\d{4}/, "")
        address = address.toString().replaceAll(/(?ism)^,/, "")
        address = address.toString().replaceAll(/(?ism)(^.*?$)/, { def a, b -> return b + " , USA" })
        address = address.toString().replaceAll(/(?s)\s+/, " ").trim()
        return address
    }

    def sanitize_reason_mortagePdf(def reason) {
        reason = reason.toString().replaceAll(/(?ism); /, "")
        reason = reason.toString().replaceAll(/(?ism)worth requirement net/, "net worth requirement")
        reason = reason.toString().replaceAll(/(?ism)(violations discovered)/, { def a, b -> return b + " during exam" })
        reason = reason.toString().replaceAll(/%%%%/, "").replaceAll(/(?ism)address\/phone/, "address/phone number")
        return reason
    }

    def getDataFrom_mortagehtml(def mortgage_html) {
        def rowData
        def rowMatcher = mortgage_html =~ /(?ism)<tr>*.*?\d{1,2}(?:\\/|-)\d{1,2}(?:\\/|-)\d{2,4}.+?<\\/tr>/
        while (rowMatcher.find()) {
            def name, address, alias
            def dateList = []
            def subUrlList = []
            def reasonList = []
            rowData = rowMatcher.group(0)

            def date_name_Matcher = rowData =~ /(?ism)<tr.+?(\d{1,2}(?:\/|-)\d{1,2}(?:\/|-)\d{2,4}).*?<.*?<td.+?<td.*?>(.+?)<\\/td/

            if (date_name_Matcher.find()) {
                dateList.add(date_name_Matcher.group(1))
                name = sanitizeMortageName(date_name_Matcher.group(2))
            }

            def url_reason_Matcher = rowData =~ /(?ism)<tr.+?\d{1,2}(?:\\/|-)\d{1,2}(?:\\/|-)\d{2,4}.*?td.+?<td.+?<td.+?href=".*?(\d{4}\\/.+?)">(.+?)<\\/td>/
            def url_reason_Matcher02 = rowData =~ /(?ism)<tr.+?\d{1,2}(?:\/|-)\d{1,2}(?:\/|-)\d{2,4}.*?td.+?<td.+?<td.+?href=".*?(\d{4}\/.+?)">(.+?)<a href="(\d{4}\/.+?)">(.+?)<\/td>/
            if (url_reason_Matcher02.find()) {
                subUrlList.add(sanitizeCsvUrl("https://idfpr.illinois.gov/content/dam/soi/en/web/idfpr/banks/resfin/discipline/" + url_reason_Matcher02.group(1)))
                reasonList.add(sanitizeMortageReason(url_reason_Matcher02.group(2)))
                subUrlList.add(sanitizeCsvUrl("https://idfpr.illinois.gov/content/dam/soi/en/web/idfpr/banks/resfin/discipline/" + url_reason_Matcher02.group(3)))
                reasonList.add(sanitizeMortageReason(url_reason_Matcher02.group(4)))
            } else if (url_reason_Matcher.find()) {
                subUrlList.add(sanitizeCsvUrl("https://idfpr.illinois.gov/content/dam/soi/en/web/idfpr/banks/resfin/discipline/" + url_reason_Matcher.group(1)))
                reasonList.add(sanitizeMortageReason(url_reason_Matcher.group(2)))
            }

            if (name != null) {
                def multipleName = separateMultipleName(name)
                multipleName.each {
                    def aliasList = []
                    if (it) {
                        def nameAliasList = splitNameAlias(it)
                        it = nameAliasList[0]
                        if (nameAliasList.size() > 1) {
                            aliasList.add(nameAliasList[1])
                        }
                        entityCreation(it, subUrlList, reasonList, dateList, aliasList, address)
                    }
                }
            }
        }
    }

    def separateMultipleName(def name) {
        def multipleName
        def multipleNameMatcher = name =~ /(?ism)aaaaa|c\/o/
        if (multipleNameMatcher.find()) {
            multipleName = name.toString().split("(?ism)aaaaa|c/o")
        } else {
            multipleName = [name]
        }
        return multipleName
    }

    def splitNameAlias(def name) {
        def nameAliasList = []
        def aliasMatcher = name =~ /(?ism);\s\s\s|dba\s|d\/b\/a|F\/K\/A|A\/K\/A|A\.K\.A/

        if (aliasMatcher.find()) {
            nameAliasList = name.split("(?ism);   |dba |d/b/a|F/K/A|A/K/A|A.K.A").collect({ its -> return its })
        } else {
            nameAliasList[0] = name
        }
        return nameAliasList
    }

    def sanitizeMortageName(def name) {
        name = name.toString().replaceAll(/(?ism)&amp;/, "&").replaceAll(/(?ism)&rsquo;/, "'")
        name = name.toString().replaceAll(/(?ism)(?:<p>)*<span class="DefaultCells">/, "")
        name = name.toString().replaceAll(/(?ism)<\/span>|<\/p>|<p>|&nbsp;/, "")
        name = name.toString().replaceAll(/(?ism)@/, "")
        name = name.toString().replaceAll(/(?ism); M/, ";     M")
        name = name.toString().replaceAll(/(?ism)(FIDELITY.+?ILLINOIS)(.*?)(BLUE.+?inc)(.*?)(fid.+)/, { def a, b, c, d, e, f -> return b + " dba " + d + " dba " + f })
        name = name.toString().replaceAll(/(?ism)(AVATAR.+?, INC.)(.+?)(MONROE.+?INC.)(.+?)(MONROE.+?CORPORATION)(.+?)(ARTHUR.+)/, { def a, b, c, d, e, f, g, h -> return b + " dba " + d + " dba " + f + " aaaaa " + h })
        name = name.toString().replaceAll(/(?ism)<br \/>|, -/, "")
        name = name.toString().replaceAll(/(?ism)(OCWEN.+?LLC)(.*?)(HOMEWARD.+?INC\.)(.*?)(LIBERTY.+)/, { def a, b, c, d, e, f -> return b + " aaaaa " + d + " aaaaa " + f })
        name = name.toString().replaceAll(/(?ism),\s*\u0024/, "").replaceAll(/(?ism)Inc\.,/, "Inc.")
        name = name.toString().replaceAll(/(?ism)(JESUS MENDOZA.+?)(<br>.*?)(jm)/, { def a, b, c, d -> return b + " aaaaa " + d })
        name = name.toString().replaceAll(/<br>/, "")
        name = name.toString().replaceAll(/Blue Chicago Financial Corp\./, "Blue Chicago Financial Corp")
        return name
    }

    def sanitizeMortageReason(def reason) {
        reason = reason.toString().replaceAll(/(?ism)&amp;/, "&")
        reason = reason.toString().replaceAll(/(?ism)<\/a>|<\/strong>|<\/span>|<\/p>|<strong>/, "")
        reason = reason.toString().replaceAll(/(?ism)(?:Settlement of\s|E|No\. )*\d{4}\-mbr-\d{2,}.*?<br \/>/, "")
        reason = reason.toString().replaceAll(/(?ism)No\. E2008-48-MBR<br \/>|E2008-48-MBR-b/, "")
        reason = reason.toString().replaceAll(/(?ism)2011-MBR-17-b &|2011-MLO-25-b|2011-MLO-22|2011-MBR-CD13/, "")
        reason = reason.toString().replaceAll(/(?ism)\d{4}-\s*mbr-.*?\d{2,4}(?:b|-b|-c)*|(?ism)\d{4}-mbr*-(?:CD1|CD2|CD-01b*|CD7-*b*|CD6-*b*|CD5|CD4|CD3-*c*b*|CD8|CD9-*b*)*/, "")
        reason = reason.toString().replaceAll(/(?ism)<p>|&nbsp;|NOTE:.+?ent Order|<br>|(?:-b)*<br \/>|CD-01-b<br>|2007-85|(?ism)<a href.+?">/, "")
        reason = reason.toString().replaceAll(/917/, "Order to Cease & Desist").replaceAll(/(?ism)No. E2008-148/, "")
        reason = reason.toString().replaceAll(/(– |-\s*)(\w+)/, '$2')
        reason = reason.toString().replaceAll(/(?s)<span class.+?r appeal\./, "").replaceAll(/^\s+/, "")
        reason = reason.toString().replaceAll(/(?s)\s+/, " ").trim()
        return reason
    }

    def getDataFromPawnBrokerHtml(def pawnbrokerhtml) {
        def rowData
        def rowMatcher = pawnbrokerhtml =~ /(?ism)<tr>.*?\d{1,2}\/\d{1,2}\/\d{2,4}.+?<\/tr>/
        while (rowMatcher.find()) {
            def name
            def dateList = []
            def address, alias
            def aliasList = []
            def subUrlList = []
            def reasonList = []
            rowData = rowMatcher.group(0)


            def date_name_Matcher = rowData =~ /(?ism)<tr>.+?>\s*(\d{1,2}\/\d{1,2}\/\d{2,4})\s*<.+?<td.+?<td.+?.+?>(.+?)<\//

            if (date_name_Matcher.find()) {
                dateList.add(date_name_Matcher.group(1))
                name = pawnBrokerSanitizeName(date_name_Matcher.group(2))
            }
            def addressMatcher = rowData =~ /(?ism)<tr.+?\/\d{4}<\/td>.+?<td.+?<td.+?<br\s\/>(.+?)<\/td>.+?cbt\/pawnbrok/
            if (addressMatcher.find()) {
                address = addressMatcher.group(1)
            }

            if ((name.toString().contains("d/b/a")) || name.toString().contains("D/B/A")) {
                (alias, name) = separateAlias(name)
                if (alias) {
                    aliasList.add(alias)
                }
            }

            if ((name.toString().contains("DBA")) || (name.toString().contains("dba"))) {
                (alias, name) = separateAlias(name)
                if (alias) {
                    aliasList.add(alias)
                }
            }

            def url_reason_Matcher03 = rowData =~ /(?ism)<tr>.+?>\s*\d{1,2}\/\d{1,2}\/\d{2,4}.+?<td.+?<td.+?<td.+?href=".*?((?:\/cbt.+?)\d{4}.+?)".*?>(.+?)<\/td>/
            def url_reason_Matcher = rowData =~ /(?ism)<tr>.+?>\s*\d{1,2}\/\d{1,2}\/\d{2,4}.+?<td.+?<td.+?<td.+?href=".*?(\d{4}.+?)".*?>(.+?)<\/td>/

            if (url_reason_Matcher03.find()) {
                subUrlList.add(sanitizeCsvUrl("https://idfpr.illinois.gov/content/dam/soi/en/web/idfpr/banks" + url_reason_Matcher03.group(1)))
                reasonList.add(pawnBrokerReasonSanitize(url_reason_Matcher03.group(2)))
            } else if (url_reason_Matcher.find()) {
                subUrlList.add(sanitizeCsvUrl("https://idfpr.illinois.gov/content/dam/soi/en/web/idfpr/banks" + url_reason_Matcher.group(1)))
                reasonList.add(pawnBrokerReasonSanitize(url_reason_Matcher.group(2)))

            }
            entityCreation(name, subUrlList, reasonList, dateList, aliasList, address)
        }

    }

    def pawnBrokerSanitizeName(def name) {
        name = name.toString().replaceAll(/(?ism)<br \/>.+|<span class="style11">/, "")
        name = name.toString().replaceAll(/(?ism)&amp;/, "&").replaceAll(/(?ism)&rsquo;/, "'")
        name = name.toString().replaceAll(/(?ism),.*?Jr\. - /, "")
        name = name.toString().replaceAll(/(?ism)son -/, "son")
        name = name.toString().replaceAll(/(?ism)ardo.*?-/, "ardo")
        name = name.toString().replaceAll(/(?ism)\s+/, " ").trim()
        return name
    }

    def pawnBrokerReasonSanitize(def reason) {
        reason = reason.toString().replaceAll(/(?ism)<p>vacated.+?href.+?5\/24\/05|<span class="style11">/, "")
        reason = reason.toString().replaceAll(/(?ism)&amp;/, "&")
        reason = reason.toString().replaceAll(/(?ism)<a.+?\/(?:PAWNBROK|B&BPawn|PrairieS|ThePawn|LipaEnter|Americas|Pontiac|Premie|CarolStr).+?\.pdf">/, "")
        reason = reason.toString().replaceAll(/(?ism)<strong>|<\/strong>|<\/a>|<\/p>|<BR>|<\/SPAN>|<br \/>/, "")
        return reason
    }

    def getDataFromBanksHtml(def banks_html) {
        def rowData
        def rowMatcher = banks_html =~ /(?ism)<tr>.*?\d{1,2}\\/\d{1,2}\\/\d{2,4}.+?<\\/tr>/
        while (rowMatcher.find()) {
            def date = []
            def name, suburl, reason, alias
            def subUrlList = []
            def reasonList = []
            def aliasList = []
            rowData = rowMatcher.group(0)

            def date_name_Matcher = rowData =~ /(?ism)<tr>.+?>\s*(\d{1,2}\/\d{1,2}\/\d{2,4})\s*<.+?<td.+?<td.+?.+?>(.+?)<\//
            def person_matcher = rowData =~ /(?ism)<td.+?(?:03\\/30\\/2004|09\\/03\\/2003).+?<td.+?<td.+?<td.+?>(.+?)<.+?(?=Penalty|Prohibition)/
            if (date_name_Matcher.find()) {
                date.add(date_name_Matcher.group(1))
                name = nameSanitize(date_name_Matcher.group(2))

            }
            if (person_matcher.find()) {
                name = nameSanitize(person_matcher.group(1))
            }

            if ((name.toString().contains("d/b/a")) || name.toString().contains("D/B/A")) {
                (alias, name) = separateAlias(name)
                if (alias) {
                    aliasList.add(alias)
                }
            }

            def url_reason_Matcher = rowData =~ /(?ism)<tr>.+?>\s*\d{1,2}\/\d{1,2}\/\d{2,4}.+?<td.+?<td.+?<td.+?href=".*?(\d{4}.+?)".*?>(.+?)<\/td>/
            def url_reason_Matcher02 = rowData =~ /(?ism)<tr>.+?>\s*\d{1,2}\/\d{1,2}\/\d{2,4}.+?<td.+?<td.+?<td.+?href=".*?(\d{4}.+?)".*?>(.+?)<\/a><br>.+?href=".*?(\d{4}.+?)">(.+?)<\/a><\/td>.*?<\/tr>/

            if (url_reason_Matcher02.find()) {
                subUrlList.add(sanitizeCsvUrl("https://idfpr.illinois.gov/content/dam/soi/en/web/idfpr/banks/cbt/enforcement/" + url_reason_Matcher02.group(1)))
                reasonList.add(reasonSanitize(url_reason_Matcher02.group(2)))
                subUrlList.add(sanitizeCsvUrl("https://idfpr.illinois.gov/content/dam/soi/en/web/idfpr/banks/cbt/enforcement/" + url_reason_Matcher02.group(3)))
                reasonList.add(reasonSanitize(url_reason_Matcher02.group(4)))

            } else if (url_reason_Matcher.find()) {
                subUrlList.add(sanitizeCsvUrl("https://idfpr.illinois.gov/content/dam/soi/en/web/idfpr/banks/cbt/enforcement/" + url_reason_Matcher.group(1)))
                reasonList.add(reasonSanitize(url_reason_Matcher.group(2)))
            }
            def address = null

            if (name != null) {
                def multipleName = separateMultipleName(name)
                multipleName.each {
                    if (it) {
                        it = nameMultipleSanitize(it)
                        entityCreation(it, subUrlList, reasonList, date, aliasList, address)
                    }
                }
            }
        }
    }

    def sanitizeCsvUrl(def url) {
        url = url.toString().replaceAll(/\(/, "%28").replaceAll(/\)/, "%29")
        url = url.toString().replaceAll(/(?ism)&amp;/, "&")
        url = url.toString().replaceAll(/(?ism)'/, "%27")
        url = url.toString().replaceAll(/(?ism)"%20target="new/, "")
        url = url.toString().replaceAll(/(?ism)\s/, "%20")
        return url
    }

    def nameMultipleSanitize(def name) {
        name = name.toString().replaceAll(/(?s)^\s{1,}/, "")
        return name
    }

    def nameSanitize(def name) {
        name = name.toString().replaceAll(/(?ism)<p>|<SPAN>/, "")
        name = name.toString().replaceAll(/(?ism)&amp;/, "&").replaceAll(/(?ism)&rsquo;/, "'")
        name = name.toString().replaceAll(/(?ism)LTD - /, "LTD")
        name = name.toString().replaceAll(/(?ism)(John Lohmeier)(,\s)(.+?)(,\sand)(.+)/, { def a, b, c, d, e, f -> return b + " aaaaa " + d + " aaaaa " + f })
        name = name.toString().replaceAll(/(?ism),\s(SB, Osw|Buffalo|Des Plaines|Chicago|Springfield|Nauvoo|Savanna|Grand|Sterling|Harvard|St. Elmo|Varna|Varna|Kenney|Shabbona|Berwyn|Chatsworth|Sainte|Illinois|Joliet).*/, "")
        name = name.toString().replaceAll(/(?ism)\s+/, " ").trim()
        return name
    }

    def reasonSanitize(def reason) {
        reason = reason.toString().replaceAll(/(?ism)<strong>|<\/strong>|<\/a>|<\/p>|<BR>|<\/SPAN>|<br \/>/, "")
        reason = reason.toString().replaceAll(/(?ism)(^Consent\s*.*?\n*order\sentered on\s3\\/29\\/06)/, { def a, b -> return "Cease and Desist Order superseded by " + b })
        reason = reason.toString().replaceAll(/(?ism)&amp;/, "&")
        return reason
    }

    def sanitizeAlias(def alias) {
        alias = alias.toString().replaceAll(/(?ism)\s+/, " ").trim()
        alias = alias.toString().replaceAll(/(?ism)null/, "")
        alias = alias.toString().replaceAll(/(?ism)\/ Firststreet.com/, "Firststreet.com")
        return alias
    }

    def entityCreation(def name, def subUrlList, def reasonList, def eventDateList, def aliasList, def address) {
        def entity = null
        if (!name.toString().isEmpty()) {
            name = name.toString().replaceAll(/Corp\.,/, "Corp.").trim()
            def entityType = detectEntity(name)
            entity = context.findEntity("name": name, "type": entityType)

            if (!entity) {
                entity = context.getSession().newEntity()
                entity.setName(name)
                entity.setType(entityType)
            }

            if (aliasList) {
                aliasList.each {
                    if (it) {
                        it = it.toString().replaceAll(/(?s)\s{2,}/, " ").trim()
                        entity.addAlias(it)
                    }
                }
            }

            if (address) {
                if (!address.toString().isEmpty()) {
                    def addrMap = addressParser.parseAddress([text: address, force_country: true])
                    ScrapeAddress scrapeAddress = addressParser.buildAddress(addrMap, [street_sanitizer: street_sanitizer])
                    if (scrapeAddress) {
                        entity.addAddress(scrapeAddress)
                    }
                }
            }

            if (!address) {
                ScrapeAddress scrapeAddress = new ScrapeAddress()
                scrapeAddress.setProvince("Illinois")
                scrapeAddress.setCountry("UNITED STATES")
                entity.addAddress(scrapeAddress)
            }

            if (subUrlList) {
                subUrlList.each {
                    if (it) {
                        it = it.toString().replaceAll(/(?ism)(\.pdf)(.+)/, '$1')
                        entity.addUrl(it)

                    }
                }
            }

            ScrapeEvent event = new ScrapeEvent()
            if (reasonList) {
                reasonList.each {
                    if (it) {
                        def description2 = description + " Enforcement Action: " + it
                        event.setDescription(description2.replaceAll(/(?s)\s+/, " ").trim())
                    }
                }
            } else {
                event.setDescription(description)
            }

            if (eventDateList) {
                eventDateList.each {
                    if (it) {
                        it = context.parseDate(new StringSource(it), ["MM/dd/yy", "MM-dd-yy", "MM/dd/yyyy", "MM-dd-yyyy"] as String[])
                        event.setDate(it)

                    }
                }
            }
            entity.addEvent(event)
        }
    }

    def street_sanitizer = { street -> fixStreet(street)
    }

    def fixStreet(street) {
        street = street.toString().replaceAll(/(?s)\s{2,}/, " ").trim().replaceAll(/(?s)\s+/, " ").trim()
        return street
    }

    def separateAlias(def name) {
        def alias
        def aliasRegex = name =~ /(?ism)d\/b\/a(.+)/
        def aliasRegex02 = name =~ /(?ism)dba(.+)/
        if (aliasRegex.find()) {
            alias = sanitizeAlias(aliasRegex.group(1))
            name = name.toString().replaceAll(/$alias/, "").replaceAll(/(?ism)d\/b\/a/, "")
        }
        if (aliasRegex02.find()) {
            alias = sanitizeAlias(aliasRegex02.group(1))
            name = name.toString().replaceAll(/$alias/, "").replaceAll(/(?ism)dba/, "")
        }
        return [alias, name]
    }


    def detectEntity(def name) {
        def type

        if (name =~ /^\S+$/) {
            type = "O"
        } else {
            if (name =~ /(?i)(?:pharmacy|&|Co\.|National|Trader|Nugget|WHOLESALE|The|Trading|Inc|FINANCE|Exchange|CONSULTING|Team|Services|UNITED|Community|Lending|Initiatives|Administration|Stone|MANAGEMENT|Enterprise|Cash|Exquisite|Worth|Shop|legal|loan|\.com|Jewelers|Jewelry|Pawn|Capital|MORTGAGE|BANK|FINANCIAL|AMERICA|Solutions|Diamond|FAMILIA|Illinois|Chicago|First|AMERICAN|CORP|DIAGNOSTICS|VISITING|HOME|DENTAL|FAMILY|ASSOCIATES|MEDICAL|CARE|HEALTH|Limited|COACH|HEALING|PLANT|CHINIA PLANT|STREET|Company|Ltd|LLC|L\.P\.|INC\.|insurance|association|corp |corporation|\blab\b|\bservice\b|\bgroup\b|nursing|MEDICAL LIVERY|PHARMACEUTICAL|PSYCHIATRY|TRANSPORTATION|LABORATORY|MEDICAL|CENTER|\b(?:END|CHOICE|HOME|VALLEY|CLINICAL|AVENUE|DIAGNOSTIC|FAMILY ENRICHMENT)\b)/) {
                type = "O"
            } else {
                type = "P"
            }
        }
        return type
    }

    def invoke(url, cache = false, tidy = false) {
        return context.invoke([url: url, tidy: tidy, cache: cache])
    }
}