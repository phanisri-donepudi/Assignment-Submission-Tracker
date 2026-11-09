const $ = id => document.getElementById(id);
const enc = data => new URLSearchParams(data);
$("today").textContent = new Date().toLocaleDateString(undefined,{weekday:"long",year:"numeric",month:"long",day:"numeric"});
async function api(url, options={}) {
  const r = await fetch(url, options);
  const data = await r.json();
  if (!r.ok) throw new Error(data.error || "Request failed");
  return data;
}
async function refresh() {
  try {
    const [assignments, submissions] = await Promise.all([api("/api/assignments"),api("/api/submissions")]);
    $("totalAssignments").textContent = assignments.length;
    $("totalSubmissions").textContent = submissions.length;
    $("pendingReviews").textContent = submissions.filter(s=>s.status==="Pending").length;
    $("reviewed").textContent = submissions.filter(s=>s.status==="Reviewed").length;
    $("assignmentsBody").innerHTML = assignments.length ? assignments.map(a=>`<tr><td><b>${safe(a.title)}</b><br><small>${safe(a.instructions)}</small></td><td>${safe(a.subject)}</td><td>${safe(a.section)}</td><td>${safe(a.dueDate)}</td><td><button class="small-btn delete" onclick="deleteAssignment(${a.id})">Delete</button></td></tr>`).join("") : `<tr><td colspan="5">No assignments published yet.</td></tr>`;
    $("assignmentSelect").innerHTML = `<option value="">Select assignment</option>` + assignments.map(a=>`<option>${safe(a.title)}</option>`).join("");
    $("submissionsBody").innerHTML = submissions.length ? submissions.map(s=>`<tr><td><b>${safe(s.student)}</b></td><td>${safe(s.assignment)}</td><td>${safe(s.date)}</td><td>${safe(s.answer)}</td><td><span class="status ${s.status==="Reviewed"?"done":""}">${s.status}</span></td><td>${s.status==="Reviewed"?`${s.marks}/100<br>${safe(s.feedback)}`:`<form class="review-form" onsubmit="reviewSubmission(event,${s.id})"><input name="marks" type="number" min="0" max="100" placeholder="Marks" required><input name="feedback" placeholder="Feedback"><button>Save</button></form>`}</td></tr>`).join("") : `<tr><td colspan="6">No submissions received yet.</td></tr>`;
  } catch(e) { console.error(e); }
}
function safe(v){return String(v??"").replace(/[&<>"']/g,c=>({"&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;","'":"&#39;"}[c]));}
$("assignmentForm").addEventListener("submit",async e=>{
  e.preventDefault(); const data=Object.fromEntries(new FormData(e.target));
  try{await api("/api/assignments",{method:"POST",headers:{"Content-Type":"application/x-www-form-urlencoded"},body:enc(data)});e.target.reset();$("assignmentMessage").textContent="Assignment published successfully.";refresh();}
  catch(err){$("assignmentMessage").textContent=err.message;}
});
$("submissionForm").addEventListener("submit",async e=>{
  e.preventDefault();const data=Object.fromEntries(new FormData(e.target));
  try{await api("/api/submissions",{method:"POST",headers:{"Content-Type":"application/x-www-form-urlencoded"},body:enc(data)});e.target.reset();$("submissionMessage").textContent="Submission saved successfully.";refresh();}
  catch(err){$("submissionMessage").textContent=err.message;}
});
async function deleteAssignment(id){if(!confirm("Delete this assignment?"))return;try{await api("/api/assignments?id="+id,{method:"DELETE"});refresh();}catch(e){alert(e.message);}}
async function reviewSubmission(event,id){event.preventDefault();const data=Object.fromEntries(new FormData(event.target));data.id=id;try{await api("/api/submissions",{method:"PATCH",headers:{"Content-Type":"application/x-www-form-urlencoded"},body:enc(data)});refresh();}catch(e){alert(e.message);}}
$("refresh").addEventListener("click",refresh);
refresh();
