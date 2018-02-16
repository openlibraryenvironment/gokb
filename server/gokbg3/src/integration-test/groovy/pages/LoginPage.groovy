package pages

class LoginPage extends GokbPage {
    // static url = "/gokb"
    static url = "/gokb";

    static at = { 
      browser.page.title.startsWith "Login" 
    };


    // Find useful links and make them available to spec objects
    static content = {
        // loginLink {
        //     $("a", text: "Sign In").click()
        // }
    }
}
