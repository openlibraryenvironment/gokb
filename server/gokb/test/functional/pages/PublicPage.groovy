package pages

class PublicPage extends GokbPage {
    // static url = "/gokb"
    static url = "http://localhost:80080/gokb"
    static at = { 
      browser.page.title.startsWith "GOKb: Welcome" 
    };


    // Find useful links and make them available to spec objects
    static content = {
        // loginLink {
        //     $("a", text: "Sign In").click()
        // }
    }
}
