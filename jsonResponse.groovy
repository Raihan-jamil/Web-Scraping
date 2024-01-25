package current_scripts


import com.rdc.importer.scrapian.ScrapianContext
import com.opencsv.CSVWriter
import com.rdc.rdcmodel.model.Alias
import java.util.regex.Pattern

context.setup([connectionTimeout: 20000, socketTimeout: 25000, retryCount: 1, multithread: true, userAgent: "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/75.0.3770.80 Safari/537.36"])
context.session.encoding = "UTF-8" //change it according to web page's encoding
context.session.escape = true

JsonResponse script = new JsonResponse(context)
script.initParsing()


class JsonResponse {
    final ScrapianContext context
    def url = "https://www.banking.nh.gov/content/api/documents?type=document&sort=title%7Casc%7CALLOW_NULLS&page=1&size=100"
    def url2 = "https://www.banking.nh.gov//enforcement-orders"

    def writer = new CSVWriter(new FileWriter('output.csv'))
    def header = ['Main Entity Name', 'Alias', 'Entity URL', 'Event Date', 'Event Description']

    def pdfLinkList = []
    def nameLinkList = []
    def dateLinkList = []

    JsonResponse(context) {
        this.context = context
    }

    def initParsing() {
        writer.writeNext(header as String[])
        def html2 = invokeUrl(url2)
//        def html = invokeUrl(url)

//        println(html2)

        def atodregex = html2 =~ /(\/archive-[a-d])/


        while (atodregex.find()) {
            def atod = atodregex.group(0)
//            println(atod)
            atod = url2 + atod
//             println(atod)
//            if(atod=="https://www.banking.nh.gov//enforcement-orders/archive-d") {

            def archHtml = invokeUrl(atod)

//                break
//            println(archHtml)
            def pdfLink = archHtml =~ /<li.+?<a\shref=\"(.+?)\"/
            def nameLink = archHtml =~ /(?<=\<h3>)(\w(.*?))\d/
            def dateLink = archHtml =~ /((0?[1-9]|1[012])(\/)(0?[1-9]|1[0-9]|2[0-9]|3[01])(\/)(\d\d))/


//            def scrap = [] as List
            def nameArray = []

            while (pdfLink.find() && nameLink.find() && dateLink.find()) {
                def name = nameLink.group(0)

                name = name.replaceAll("\\,|\\d", "")
                name = name.replaceAll("&amp;", "&")
                name = name.replaceAll(/(,)(\s*(?:Inc)\.*)/, '$2')
                name = name.replaceAll(/ and d\/b\/a/, " and ")
                name = name.replaceAll(/L and M/, "L & M")

//                createCSV(name)

                nameArray = name.split(" and |,  and|,")

//                println("Name Array : " + nameArray)


                for(int i = 0; i < nameArray.size(); i++){

                    //  println("Entity Name: " + it)
                    def alias
                    def aliasList = []
                    def aliasregex = nameArray[i] =~ /(\(.*?\))/

                    while (aliasregex.find()) {
                        alias = aliasregex.group(1)
                        aliasList.add(alias)
                    }

                    aliasList.each {
//                        println("Alias: " + it)
                        nameArray[i] = nameArray[i].replaceAll(/$it/, "")
                        it = sanitizeAlias(it)
                    }

                    name = sanitizeName(name)
//                    println(name)
//                    println("=============")
                    def pdf = pdfLink.group(0).replaceAll("<li><a href=\"", "")
                    pdf = pdf.replaceAll("pdf\\\"", "pdf")
                    def date = dateLink.group(0)


//                    def scrap = [] as List
//                    scrap.add(name)
//                    scrap.add(alias)
//                    scrap.add(pdf)
//                    scrap.add(date)
//                    scrap.add("I am learning to scrape html data")
//                    writer.writeNext(scrap as String[])
                    createCSV(nameArray[i], aliasList, pdf, date)
//                    createCSV(it)

//                    println(name)
//                    println(alias)
//                    println(pdf)
//                    println(date)
//                    println("==============")

                }
            }
        }
//        writer.close()
        def html = invokeUrl(url)

        def aliasList1 = []
//        println(html)
        def htmlMatcher = html =~ /(?s)"(http.*?pdf)"/
        def dateMatcher = html =~ /((0?[1-9]|1[012])(\\[\/\-])(0?[1-9]|1[0-9]|2[0-9]|3[01])(\\[\/\-])(20[0-4][0-9]|2040))/
        def nameMatcher = html =~ /(?<=\["\\u003Cp\\u003E)(\w(.*?))\d/

        def nameArray = []
        while (htmlMatcher.find() && dateMatcher.find() && nameMatcher.find()) {
            def pdf = htmlMatcher.group(0).replaceAll("\\\\", "")
            def name = nameMatcher.group(0).replaceAll("\\\\|\\,", "")


            name = name.toString().replaceAll(/(and)( \w\/\w\/\w)/, "f/k/a")
            name = name.toString().replaceAll(/Inc. John/, "Inc.;John")
            name = name.toString().replaceAll(/P. Lader/, "P.;Lader")
            name = name.toString().replaceAll(/(Esquire\s)(\w+)/, '$1;$2')
            name = name.toString().replaceAll(/\) Daniel/, ");Daniel")

//            createCSV(name)

            def aliasList = []
//             println("Name: " + name)

            nameArray = name.split(" and |,  and|,|;")
            for(int i = 0; i < nameArray.size(); i++){
//                 println("Name: " + it)

                def alias
                def aliasMatch = nameArray[i] =~ /(\(.*?\))/

                def date = dateMatcher.group(0).replaceAll("\\\\", "")

                createCSV(nameArray[i], aliasList, pdf, date)
            }

        }

//       println html
    }


    def splitNameAlias(def name) {
        def nameAliasList = []
        def aliasMatcher = name =~ /(?ism)d\\/b\\/a|a\\/k\\/a|n\\/k\\/a|f\\/k\\/a/

        if (aliasMatcher.find()) {
            nameAliasList = name.split("(?ism)d\\/b\\/a|a\\/k\\/a|n\\/k\\/a|f\\/k\\/a").collect({ its -> return its })
        } else {
            nameAliasList[0] = name
        }
        return nameAliasList
    }

    def sanitizeAlias(def alias) {
        alias = alias.replaceAll("a/k/a|d/b/a", "")

        return alias
    }

    def sanitizeName(def name) {
        name = name.replaceAll("\\(\\)", "")

        return name
    }

    def createCSV(name, aliasList, url, date) {
        def alias = ""
        def scrap = [] as List
        def des = "I am learning to scrape html data"

        name = name.toString().replaceAll(/\(.*?\)/,"").trim()
        name = name.toString().replaceAll(/\)/,"").trim()


        def aliasSplitter = name =~ /(.*?)\((.*?)$/

        if(aliasSplitter.find()){
            name = aliasSplitter.group(1)
            aliasList.add(aliasSplitter.group(2))
        }

        def aliasSplitterForDBA = name =~ /(.*?)d\/b\/a(.*?)$/

        if(aliasSplitterForDBA.find()){
            name = aliasSplitterForDBA.group(1)
            aliasList.add(aliasSplitterForDBA.group(2))
        }

        name = name.toString().replaceAll(/d\/b\/a|a\/k\/a|f\/k\/a/,"-ALIAS-")

        def aliasSplitterForALIAS = name =~ /(.*?)-ALIAS-(.*?)$/

        if(aliasSplitterForALIAS.find()){
            name = aliasSplitterForALIAS.group(1)
            aliasList.add(aliasSplitterForALIAS.group(2))
        }

        name = name.toString().replaceAll(/\d+$/,'').trim()
        scrap.add(name)

//        println("Final name : "+ name)
//        println(aliasList)
        if (aliasList) {
            aliasList.each {
                it = it.toString().replaceAll(/\(|\)/,"").trim()
                it = it.toString().replaceAll(/a\/k\/a|d\/b\/a|n\/k\/a/,"").trim()
                alias = it + ","
            }
        }
        scrap.add(alias)
        url = url.toString().replaceAll(/"/,'').trim()
        scrap.add(url)
        scrap.add(date)
        scrap.add(des)


        writer.writeNext(scrap as String[])
//        writer.close()

    }

    def invokeUrl(url, headersMap = [:], cache = true, tidy = false, miscData = [:]) {
        Map dataMap = [url: url, tidy: tidy, headers: headersMap, cache: cache]
        dataMap.putAll(miscData)
        return context.invoke(dataMap)
    }
}








