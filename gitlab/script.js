$("body").attr("id","gitlab"+(document.location.pathname).replace("/", "-"));

// Disable transition at startup */
window.setTimeout('$("body").addClass("loaded")',300);

// Dynamic Title
if($("input[name='query']").length == 1){
	var html = '<img src="https://git.sapk.fr/assets/common/images/gitbucket.png">';
	html += $(".container>.head").html();
    $("a.brand").html(html);
    $("a.brand img:eq(1)").remove();
    $(".container>.head").html($(".container>.head>img")[0].outerHTML);
}else if($(".block>.account-username").length == 1){
	var html = '<img src="https://git.sapk.fr/assets/common/images/gitbucket.png">';
    	html += "<a href='"+$(".block>.account-username").text()+"'>"+$(".block>.account-username").text()+"</a>";

    $("a.brand").html(html);

}
// END dynamic title


/**
//maxcdn.bootstrapcdn.com/bootstrap/3.2.0/css/bootstrap.min.css
//maxcdn.bootstrapcdn.com/bootstrap/3.2.0/js/bootstrap.min.js
**/



//.sidemenu TODO
//$(".container>.head").parent()
if($("div[style='width: 170px;']>ul.sidemenu").length == 1) {
	var el = $(".sidemenu").parent();
	$(".container>.head").parent().append("<ul class='nav nav-tabs gitlab-nav' >"+el.find(".sidemenu").hide().html()+"</ul>")
	$(".body>.pull-right").removeClass("pull-right").addClass("well").addClass("gitlab-url")
	$(".body>div[style='margin-right: 180px;']").attr("style","");
    
    $(".gitlab-url>.input-append").addClass("input-prepend")
        		.prepend('<button class="btn" onclick="$(\'#repository-url-ssh\').click()" >SSH</button>')
    		.prepend('<button class="btn" onclick="$(\'#repository-url-http\').click()" >HTTP</button>');
    
    $(".gitlab-url a[href$='tar.gz']").css("width","60px").text("TAR.GZ").prepend('<i class="icon-download-alt"></i>');
    $(".gitlab-url a[href$='zip']").css("width","60px").text("ZIP").prepend('<i class="icon-download-alt"></i>');


}
    
