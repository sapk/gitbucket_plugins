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


/** Switch to botostrap 3.2
$('link[href$="assets/vendors/bootstrap/css/bootstrap.css"]').remove();
$('link[href$="assets/vendors/bootstrap/css/bootstrap-responsive.css"]').remove();


$('body').append($('<link href="//maxcdn.bootstrapcdn.com/bootstrap/3.2.0/css/bootstrap.min.css" rel="stylesheet">'))


//			.append($('<link href="//maxcdn.bootstrapcdn.com/bootstrap/3.2.0/css/bootstrap-theme.min.css" rel="stylesheet">'));

//			.append('<script src="//maxcdn.bootstrapcdn.com/bootstrap/3.2.0/js/bootstrap.min.js">');

///bootstrap/3.2.0/css/bootstrap-theme.min.css
*/