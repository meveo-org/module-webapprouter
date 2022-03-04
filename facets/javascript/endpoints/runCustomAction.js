const runCustomAction = async (parameters) =>  {
	const baseUrl = window.location.origin;
	const url = new URL(`${window.location.pathname.split('/')[1]}/rest/runCustomAction/`, baseUrl);
	return fetch(url.toString(), {
		method: 'POST', 
		headers : new Headers({
 			'Content-Type': 'application/json'
		}),
		body: JSON.stringify({
			actionCode : parameters.actionCode,
			runWith : parameters.runWith
		})
	});
}

const runCustomActionForm = (container) => {
	const html = `<form id='runCustomAction-form'>
		<div id='runCustomAction-actionCode-form-field'>
			<label for='actionCode'>actionCode</label>
			<input type='text' id='runCustomAction-actionCode-param' name='actionCode'/>
		</div>
		<div id='runCustomAction-runWith-form-field'>
			<label for='runWith'>runWith</label>
			<input type='text' id='runCustomAction-runWith-param' name='runWith'/>
		</div>
		<button type='button'>Test</button>
	</form>`;

	container.insertAdjacentHTML('beforeend', html)

	const actionCode = container.querySelector('#runCustomAction-actionCode-param');
	const runWith = container.querySelector('#runCustomAction-runWith-param');

	container.querySelector('#runCustomAction-form button').onclick = () => {
		const params = {
			actionCode : actionCode.value !== "" ? actionCode.value : undefined,
			runWith : runWith.value !== "" ? runWith.value : undefined
		};

		runCustomAction(params).then(r => r.text().then(
				t => alert(t)
			));
	};
}

export { runCustomAction, runCustomActionForm };